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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.NOTE;

@SuppressWarnings({"WeakerAccess", "unused"})
abstract class BaseProcessor extends AbstractProcessor {

    private static final String TAG = BaseProcessor.class.getSimpleName();
    private static final String NULLABLE = "Nullable";

    @NonNull
    protected Messager mMessager;

    @NonNull
    protected Elements mElements;

    @NonNull
    protected Types mTypes;

    @NonNull
    private String mTag = TAG;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        mElements = env.getElementUtils();
        mTypes = env.getTypeUtils();
        mMessager = env.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (Class c : getSupportedAnnotationClasses()) {
            types.add(c.getCanonicalName());
        }
        return types;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @NonNull
    protected abstract Class[] getSupportedAnnotationClasses();

    @NonNull
    protected String getPackageName(@NonNull TypeElement type) {
        return mElements.getPackageOf(type).getQualifiedName().toString();
    }

    @NonNull
    protected String getClassName(@NonNull TypeElement type, @NonNull String packageName) {
        int packageLen = packageName.length() + 1;
        return type.getQualifiedName().toString().substring(packageLen).replace('.', '$');
    }

    private static boolean hasAnnotationWithName(@NonNull Element element, @NonNull String simpleName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String annotationName = mirror.getAnnotationType().asElement().getSimpleName().toString();
            if (simpleName.equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isPrivate(@NonNull Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return modifiers.contains(Modifier.PRIVATE);
    }

    protected boolean isStatic(@NonNull Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return modifiers.contains(Modifier.STATIC);
    }

    protected boolean isNullable(@NonNull Element element) {
        return hasAnnotationWithName(element, NULLABLE);
    }

    protected boolean isAssignable(@NonNull TypeMirror typeMirror, @NonNull String type) {
        TypeMirror mirror = mElements.getTypeElement(type).asType();
        return mTypes.isAssignable(typeMirror, mirror);
    }

    protected boolean isSubtypeOfType(@NonNull TypeElement element, @NonNull String type) {
        return isSubtypeOfType(element.asType(), type);
    }

    protected boolean isSubtypeOfType(@NonNull TypeMirror typeMirror, @NonNull String type) {
        if (type.equals(typeMirror.toString())) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(type)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, type)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, type)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    protected String erasedType(@NonNull TypeMirror type) {
        String name = mTypes.erasure(type).toString();
        int typeParamStart = name.indexOf('<');
        if (typeParamStart != -1) {
            name = name.substring(0, typeParamStart);
        }
        return name;
    }

    @Nullable
    protected static TypeElement findEnclosingElement(@NonNull Element e) {
        Element base = e.getEnclosingElement();
        while (base != null && !(base instanceof TypeElement)) {
            base = base.getEnclosingElement();
        }

        return base != null ? TypeElement.class.cast(base) : null;
    }

    protected void setTag(@NonNull String tag) {
        mTag = tag;
    }

    protected void info(@NonNull String message, @Nullable Object... args) {
        print(message, NOTE, args);
    }

    protected void throwError(@NonNull String message, @NonNull Exception e, @Nullable Object... args) {
        error(message, args);
        throw new RuntimeException(e);
    }

    protected void error(@NonNull String message, @Nullable Object... args) {
        print(message, ERROR, args);
    }

    private void print(@NonNull String message, @NonNull Diagnostic.Kind kind, @Nullable Object... args) {
        if (args != null && args.length > 0) {
            message = String.format(message, args);
        }
        mMessager.printMessage(kind, mTag + " -- " + message);
    }
}
