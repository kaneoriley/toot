/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.oriley.toot;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

import static javax.tools.Diagnostic.Kind.ERROR;

public final class TootProcessor extends BaseProcessor {

    private static final String GET_SUBSCRIBER = "getSubscriber";
    private static final String GET_PRODUCER = "getProducer";
    private static final String OBJECT = "object";
    private static final String HOST = "host";
    private static final String CAST_HOST = "castHost";
    private static final String EVENT = "event";
    private static final String EVENT_CLASS = "eventClass";
    private static final String CLS = "cls";
    private static final String VALUE = "value";
    private static final String UNCHECKED = "unchecked";

    @NonNull
    private Filer mFiler;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        mFiler = env.getFiler();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @NonNull
    @Override
    protected Class[] getSupportedAnnotationClasses() {
        return new Class[] { Subscribe.class, Produce.class };
    }

    @Override
    public boolean process(@NonNull Set<? extends TypeElement> annotations, @NonNull RoundEnvironment env) {
        if (env.processingOver()) {
            return true;
        }

        try {
            final Map<TypeElement, EventMethodsMap> subscribeMethods = collectMethods(env, true);
            final Map<TypeElement, EventMethodsMap> produceMethods = collectMethods(env, false);

            for (TypeElement typeElement : subscribeMethods.keySet()) {
                String packageName = getPackageName(typeElement);
                writeToFile(packageName, generateSubscriberFactory(typeElement, subscribeMethods.get(typeElement)));
            }

            for (TypeElement typeElement : produceMethods.keySet()) {
                String packageName = getPackageName(typeElement);
                writeToFile(packageName, generateProducerFactory(typeElement, produceMethods.get(typeElement)));
            }
        } catch (TootProcessorException e) {
            mMessager.printMessage(ERROR, e.getMessage());
            return true;
        }

        return false;
    }

    @NonNull
    private TypeSpec generateSubscriberFactory(@NonNull TypeElement typeElement,
                                               @NonNull EventMethodsMap subscriberMethods) throws TootProcessorException {
        // Class type
        String packageName = getPackageName(typeElement);
        String className = getClassName(typeElement, packageName);

        // Constructor
        CodeBlock.Builder initBuilder = CodeBlock.builder()
                .add("$T.addAll(mSubscribedClasses", Collections.class);
        for (TypeMirror typeMirror : subscriberMethods.keySet()) {
            initBuilder.add(", $T.class", typeMirror);
        }
        initBuilder.add(");\n");

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addCode(initBuilder.build());

        // Class builder
        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className + SubscriberFactory.CLASS_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(constructor.build())
                .superclass(SubscriberFactory.class);

        // Create method
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(GET_SUBSCRIBER)
                .addModifiers(Modifier.PROTECTED)
                .addAnnotation(Nullable.class)
                .addAnnotation(Override.class)
                .addCode(generateAbstractSubscriber(typeElement, subscriberMethods))
                .returns(Subscriber.class);

        // Type parameter
        ParameterSpec.Builder parameterSpecBuilder =
                ParameterSpec.builder(TypeName.get(Object.class)
                        .annotated(AnnotationSpec.builder(NonNull.class).build()), OBJECT, Modifier.FINAL);

        return typeSpecBuilder.addMethod(methodSpecBuilder.addParameter(parameterSpecBuilder.build()).build()).build();
    }

    @NonNull
    private TypeSpec generateProducerFactory(@NonNull TypeElement typeElement,
                                             @NonNull EventMethodsMap producerMethods) throws TootProcessorException {
        // Class type
        String packageName = getPackageName(typeElement);
        String className = getClassName(typeElement, packageName);

        // Constructor
        CodeBlock.Builder initBuilder = CodeBlock.builder()
                .add("$T.addAll(mProducedClasses", Collections.class);
        for (TypeMirror typeMirror : producerMethods.keySet()) {
            initBuilder.add(", $T.class", typeMirror);
        }
        initBuilder.add(");\n");

        MethodSpec.Builder constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addCode(initBuilder.build());

        // Class builder
        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className + ProducerFactory.CLASS_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(constructor.build())
                .superclass(ProducerFactory.class);

        // Create method
        MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(GET_PRODUCER)
                .addModifiers(Modifier.PROTECTED)
                .addAnnotation(Nullable.class)
                .addAnnotation(Override.class)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                        .addMember(VALUE, "$S", UNCHECKED).build())
                .addCode(generateAbstractProducer(typeElement, producerMethods))
                .returns(Producer.class);

        // Type parameter
        ParameterSpec.Builder parameterSpecBuilder =
                ParameterSpec.builder(TypeName.get(Object.class)
                        .annotated(AnnotationSpec.builder(NonNull.class).build()), OBJECT, Modifier.FINAL);

        return typeSpecBuilder.addMethod(methodSpecBuilder.addParameter(parameterSpecBuilder.build()).build()).build();
    }

    @NonNull
    private CodeBlock generateAbstractSubscriber(@NonNull TypeElement hostType,
                                                 @NonNull EventMethodsMap subscriberMethods) throws TootProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("return new $T($N) {\n",  Subscriber.class, OBJECT)
                .add("    @$T\n", Override.class)
                .add("    protected void onEvent(@$T final $T $N, @$T final $T $N) {\n", NonNull.class, Object.class,
                        HOST, NonNull.class, Object.class, EVENT)
                .add("        $T $N = ($T) $N;\n", hostType, CAST_HOST, hostType, HOST)
                .add("        $T $N = $N.getClass();\n", Class.class, CLS, EVENT);

        boolean first = true;
        for (Map.Entry<TypeMirror, List<ExecutableElement>> entry : subscriberMethods.entrySet()) {
            List<ExecutableElement> methods = entry.getValue();
            if (methods.size() != 1) {
                throw new TootProcessorException("Invalid subscriber count [" + methods.size() + "] for eventType: " +
                        entry.getKey().getKind().name());
            }

            ExecutableElement method = methods.get(0);
            TypeMirror typeMirror = entry.getKey();
            builder.add(first ? "        if (" : "        } else if (")
                    .add("$N.equals($T.class)) {\n", CLS, typeMirror)
                    .add("            $N.$N(($T) event);\n", CAST_HOST, method.getSimpleName(), typeMirror);
            first = false;
        }

        return builder.add("        }\n").add("    }\n").add("};\n").build();
    }

    @NonNull
    private CodeBlock generateAbstractProducer(@NonNull TypeElement hostType,
                                               @NonNull EventMethodsMap producerMethods) throws TootProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("return new $T($N) {\n",  Producer.class, OBJECT)
                .add("    @$T\n", Override.class)
                .add("    @$T\n", Nullable.class)
                .add("    protected <E> E produceEvent(@$T final $T $N, @$T final $T<E> $N) {\n",
                        NonNull.class, Object.class, HOST, NonNull.class, Class.class, EVENT_CLASS)
                .add("        $T $N = ($T) $N;\n", hostType, CAST_HOST, hostType, HOST);

        boolean first = true;
        for (Map.Entry<TypeMirror, List<ExecutableElement>> entry : producerMethods.entrySet()) {
            List<ExecutableElement> methods = entry.getValue();
            if (methods.size() != 1) {
                throw new TootProcessorException("Invalid producer count [" + methods.size() + "] for eventType: " +
                        entry.getKey().getKind().name());
            }

            ExecutableElement method = methods.get(0);
            TypeMirror typeMirror = entry.getKey();
            builder.add(first ? "        if (" : "        } else if (")
                    .add("$N.equals($T.class)) {\n", EVENT_CLASS, typeMirror)
                    .add("            return (E) $N.$N();\n", CAST_HOST, method.getSimpleName());
            first = false;
        }
        builder.add("        } else {\n")
                .add("            return null;\n");

        return builder.add("        }\n").add("    }\n").add("};\n").build();
    }

    @NonNull
    private Map<TypeElement, EventMethodsMap> collectMethods(@NonNull RoundEnvironment env,
                                                             boolean subscribers) throws TootProcessorException {
        final Map<TypeElement, EventMethodsMap> methodsByClass = new HashMap<>();
        Class<? extends Annotation> annotationClass = subscribers ? Subscribe.class : Produce.class;

        for (Element e : env.getElementsAnnotatedWith(annotationClass)) {
            if (e.getKind() != ElementKind.METHOD) {
                throw new TootProcessorException(e.getSimpleName() + " is annotated with @" +
                        annotationClass.getName() + " but is not a method");
            }

            final ExecutableElement method = (ExecutableElement) e;
            String methodName = method.getSimpleName().toString();
            // methods must be public as generated code will call it directly
            if (isPrivate(method)) {
                throw new TootProcessorException("Method must not be private: " + methodName);
            } else if (isStatic(method)) {
                throw new TootProcessorException("Method must not be static: " + methodName);
            }

            final List<? extends VariableElement> parameters = method.getParameters();
            final TypeMirror returnType = method.getReturnType();

            if (subscribers) {
                // there must be only one parameter
                if (parameters == null || parameters.size() == 0) {
                    throw new TootProcessorException("Too few arguments in: " + methodName);
                } else if (parameters.size() > 1) {
                    throw new TootProcessorException("Too many arguments in: " + methodName);
                }
            } else {
                // there must be no parameters
                if (parameters != null && parameters.size() > 0) {
                    throw new TootProcessorException("Producer method cannot have parameters: " + methodName);
                } else if (returnType == null) {
                    throw new TootProcessorException("Producer method must return an event: " + methodName);
                }
            }

            // method shouldn't throw exceptions
            final List<? extends TypeMirror> exceptions = method.getThrownTypes();
            if (exceptions != null && exceptions.size() > 0) {
                throw new TootProcessorException("Method shouldn't throw exceptions: " + methodName);
            }

            final TypeElement type = findEnclosingElement(e);
            // class should exist
            if (type == null) {
                throw new TootProcessorException("Could not find a class for " + methodName);
            }
            // and it should be public
            if (isPrivate(type)) {
                throw new TootProcessorException("Class is private: " + type);
            }
            // as well as all parent classes
            TypeElement parentType = findEnclosingElement(type);
            while (parentType != null) {
                if (isPrivate(parentType)) {
                    throw new TootProcessorException("Parent class is private: " + parentType);
                }
                parentType = findEnclosingElement(parentType);
            }

            final TypeMirror eventType = subscribers ? parameters.get(0).asType() : returnType;

            EventMethodsMap methodsInClass = methodsByClass.get(type);
            if (methodsInClass == null) {
                methodsInClass = new EventMethodsMap();
                methodsByClass.put(type, methodsInClass);
            }

            List<ExecutableElement> methodsByType = methodsInClass.get(eventType);
            if (methodsByType == null) {
                methodsByType = new ArrayList<>();
                methodsInClass.put(eventType, methodsByType);
            }

            methodsByType.add(method);
        }

        return methodsByClass;
    }

    @NonNull
    private JavaFile writeToFile(@NonNull String packageName, @NonNull TypeSpec spec) throws TootProcessorException {
        final JavaFile file = JavaFile.builder(packageName, spec).indent("    ").build();
        try {
            file.writeTo(mFiler);
        } catch (IOException e) {
            throw new TootProcessorException(e);
        }
        return file;
    }

    private static final class TootProcessorException extends Exception {

        TootProcessorException(String message) {
            super(message);
        }

        TootProcessorException(Throwable cause) {
            super(cause);
        }
    }

    // Just to make the code more readable
    private static final class EventMethodsMap extends HashMap<TypeMirror, List<ExecutableElement>> {}
}