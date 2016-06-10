/*
 * Copyright (C) 2016 Sergey Solovyev
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
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

import static javax.tools.Diagnostic.Kind.ERROR;

public final class TootProcessor extends AbstractProcessor {

    private static final String DELEGATE = "delegate";
    private static final String TARGET_CLASS = "targetClass";
    private static final String SUBSCRIBER = "subscriber";
    private static final String PRODUCER = "producer";
    private static final String SUBSCRIBERS = "subscribers";
    private static final String PRODUCERS = "producers";
    private static final String CREATE_SUBSCRIBERS = "createSubscribers";
    private static final String CREATE_PRODUCERS = "createProducers";

    @NonNull
    private Filer mFiler;

    @NonNull
    private Messager mMessager;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        mFiler = env.getFiler();
        mMessager = env.getMessager();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(Subscribe.class.getCanonicalName());
        types.add(Produce.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(@NonNull Set<? extends TypeElement> annotations, @NonNull RoundEnvironment env) {
        if (env.processingOver()) {
            return true;
        }

        try {
            final Map<TypeElement, EventMethodsMap> subscribeMethods = collectMethods(env, true);
            final Map<TypeElement, EventMethodsMap> produceMethods = collectMethods(env, false);

            if (!subscribeMethods.isEmpty()) {
                writeToFile(generateClass(subscribeMethods, produceMethods));
            }
        } catch (TootProcessorException e) {
            mMessager.printMessage(ERROR, e.getMessage());
            return true;
        }

        return false;
    }

    @Nullable
    private static TypeElement findEnclosingElement(@NonNull Element e) {
        Element base = e.getEnclosingElement();
        while (base != null && !(base instanceof TypeElement)) {
            base = base.getEnclosingElement();
        }

        return base != null ? TypeElement.class.cast(base) : null;
    }

    @NonNull
    private TypeSpec generateClass(@NonNull Map<TypeElement, EventMethodsMap> subscribeMethodsInClass,
                                   @NonNull Map<TypeElement, EventMethodsMap> produceMethodsInClass) throws TootProcessorException {
        return TypeSpec.classBuilder(TootFinder.class.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(Finder.class)
                .addMethod(generateCreateMethod(subscribeMethodsInClass, true))
                .addMethod(generateCreateMethod(produceMethodsInClass, false)).build();
    }

    @NonNull
    private MethodSpec generateCreateMethod(@NonNull Map<TypeElement, EventMethodsMap> methodsByClass,
                                            boolean subscribers) throws TootProcessorException {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(subscribers ? CREATE_SUBSCRIBERS : CREATE_PRODUCERS)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, DELEGATE, Modifier.FINAL)
                .addParameter(Class.class, TARGET_CLASS, Modifier.FINAL)
                .returns(Map.class);
        for (Map.Entry<TypeElement, EventMethodsMap> entry : methodsByClass.entrySet()) {
            builder.addCode(generateElement(entry.getKey(), entry.getValue(), subscribers)).addCode("\n");
        }

        builder.addStatement("// Fall through if nothing found");
        builder.addStatement("return $T.emptyMap()", Collections.class);
        return builder.build();
    }

    @NonNull
    private CodeBlock generateElement(@NonNull TypeElement type,
                                      @NonNull EventMethodsMap eventMethodsMap,
                                      boolean subscriber) throws TootProcessorException {
        final CodeBlock.Builder builder = CodeBlock.builder()
                .beginControlFlow("if ($N.equals($T.class))", TARGET_CLASS, type);

        String local = subscriber ? SUBSCRIBERS : PRODUCERS;
        Class c = subscriber ? Subscriber.class : Producer.class;

        builder.addStatement("final $T<$T<?>, $T> $N = new $T<$T<?>, $T>($L)",
                Map.class, Class.class, c, local, HashMap.class, Class.class, c, eventMethodsMap.size());

        for (Map.Entry<TypeMirror, List<ExecutableElement>> entry : eventMethodsMap.entrySet()) {
            final TypeMirror eventType = entry.getKey();
            final List<ExecutableElement> methods = entry.getValue();
            builder.addStatement("$N.put($T.class, $L)", local, eventType, generateHolder(type, eventType, methods, subscriber));
        }

        builder.addStatement("return $N", local).endControlFlow();
        return builder.build();
    }

    @NonNull
    private CodeBlock generateHolder(@NonNull TypeElement type,
                                          @NonNull TypeMirror eventType,
                                          @NonNull List<ExecutableElement> methods,
                                          boolean subscriber) throws TootProcessorException {
        String local = subscriber ? SUBSCRIBER : PRODUCER;

        if (methods.size() != 1) {
            throw new TootProcessorException("Invalid " + local + " count [" + methods.size() + "] for eventType: " +
                    eventType.getKind().name());
        } else {
            return CodeBlock.builder().add("$L", subscriber ? generateSubscriber(type, eventType, methods.get(0)) :
                    generateProducer(type, eventType, methods.get(0))).build();
        }
    }

    @NonNull
    private CodeBlock generateSubscriber(@NonNull TypeElement type,
                                         @NonNull TypeMirror eventType,
                                         @NonNull ExecutableElement method) {
        return CodeBlock.builder()
                .add("\nnew $T<$T>($N) {",  Subscriber.class, eventType, DELEGATE)
                .add("\n    public void onEvent($T event) {", eventType)
                .add("\n        (($T) $N).$N(($T) event);", type, DELEGATE, method.getSimpleName(), eventType)
                .add("\n    }")
                .add("\n}")
                .build();
    }

    @NonNull
    private CodeBlock generateProducer(@NonNull TypeElement type,
                                       @NonNull TypeMirror eventType,
                                       @NonNull ExecutableElement method) {
        return CodeBlock.builder()
                .add("\nnew $T<$T>($N) {",  Producer.class, eventType, DELEGATE)
                .add("\n    public $T produceEvent() {", eventType)
                .add("\n        return (($T) $N).$N();", type, DELEGATE, method.getSimpleName())
                .add("\n    }")
                .add("\n}")
                .build();
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
            // methods must be public as generated code will call it directly
            if (!method.getModifiers().contains(Modifier.PUBLIC)) {
                throw new TootProcessorException("Method is not public: " + method.getSimpleName());
            }

            final List<? extends VariableElement> parameters = method.getParameters();
            final TypeMirror returnType = method.getReturnType();

            if (subscribers) {
                // there must be only one parameter
                if (parameters == null || parameters.size() == 0) {
                    throw new TootProcessorException("Too few arguments in: " + method.getSimpleName());
                } else if (parameters.size() > 1) {
                    throw new TootProcessorException("Too many arguments in: " + method.getSimpleName());
                }
            } else {
                // there must be no parameters
                if (parameters != null && parameters.size() > 0) {
                    throw new TootProcessorException("Producer method cannot have parameters: " + method.getSimpleName());
                } else if (returnType == null) {
                    throw new TootProcessorException("Producer method must return an event: " + method.getSimpleName());
                }
            }

            // method shouldn't throw exceptions
            final List<? extends TypeMirror> exceptions = method.getThrownTypes();
            if (exceptions != null && exceptions.size() > 0) {
                throw new TootProcessorException("Method shouldn't throw exceptions: " + method.getSimpleName());
            }

            final TypeElement type = findEnclosingElement(e);
            // class should exist
            if (type == null) {
                throw new TootProcessorException("Could not find a class for " + method.getSimpleName());
            }
            // and it should be public
            if (!type.getModifiers().contains(Modifier.PUBLIC)) {
                throw new TootProcessorException("Class is not public: " + type);
            }
            // as well as all parent classes
            TypeElement parentType = findEnclosingElement(type);
            while (parentType != null) {
                if (!parentType.getModifiers().contains(Modifier.PUBLIC)) {
                    throw new TootProcessorException("Class is not public: " + parentType);
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
    private JavaFile writeToFile(@NonNull TypeSpec spec) throws TootProcessorException {
        final JavaFile file = JavaFile.builder(TootFinder.class.getPackage().getName(), spec).indent("    ").build();
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