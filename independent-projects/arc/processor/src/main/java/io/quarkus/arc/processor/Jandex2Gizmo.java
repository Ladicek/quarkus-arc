package io.quarkus.arc.processor;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.InterfaceMethodDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

public class Jandex2Gizmo {
    public static ClassDesc classDescOf(DotName name) {
        // TODO arrays, using the `Type.name()` syntax
        if (name.prefix() == null) {
            String local = name.local();
            return switch (local) {
                case "void" -> ConstantDescs.CD_void;
                case "boolean" -> ConstantDescs.CD_boolean;
                case "byte" -> ConstantDescs.CD_byte;
                case "short" -> ConstantDescs.CD_short;
                case "int" -> ConstantDescs.CD_int;
                case "long" -> ConstantDescs.CD_long;
                case "float" -> ConstantDescs.CD_float;
                case "double" -> ConstantDescs.CD_double;
                case "char" -> ConstantDescs.CD_char;
                default -> ClassDesc.of(local);
            };
        }
        return ClassDesc.of(name.toString());
    }

    public static ClassDesc classDescOf(Type type) {
        return switch (type.kind()) {
            case VOID -> ConstantDescs.CD_void;
            case PRIMITIVE -> switch (type.asPrimitiveType().primitive()) {
                case BOOLEAN -> ConstantDescs.CD_boolean;
                case BYTE -> ConstantDescs.CD_byte;
                case SHORT -> ConstantDescs.CD_short;
                case INT -> ConstantDescs.CD_int;
                case LONG -> ConstantDescs.CD_long;
                case FLOAT -> ConstantDescs.CD_float;
                case DOUBLE -> ConstantDescs.CD_double;
                case CHAR -> ConstantDescs.CD_char;
            };
            case ARRAY -> {
                ArrayType arrayType = type.asArrayType();
                ClassDesc element = classDescOf(arrayType.elementType());
                yield element.arrayType(arrayType.deepDimensions());
            }
            default -> ClassDesc.of(type.name().toString());
        };
    }

    public static ClassDesc classDescOf(ClassInfo clazz) {
        return ClassDesc.of(clazz.name().toString());
    }

    public static FieldDesc fieldDescOf(FieldInfo field) {
        return FieldDesc.of(classDescOf(field.declaringClass()), field.name(), classDescOf(field.type()));
    }

    public static MethodDesc methodDescOf(MethodInfo method) {
        if (method.isConstructor() || method.isStaticInitializer()) {
            throw new IllegalArgumentException("Cannot create MethodDesc for " + method);
        }

        ClassDesc owner = classDescOf(method.declaringClass());
        ClassDesc returnType = classDescOf(method.returnType());
        ClassDesc[] paramTypes = new ClassDesc[method.parametersCount()];
        for (int i = 0; i < method.parametersCount(); i++) {
            paramTypes[i] = classDescOf(method.parameterType(i));
        }
        MethodTypeDesc methodTypeDesc = MethodTypeDesc.of(returnType, paramTypes);
        return method.declaringClass().isInterface()
                ? InterfaceMethodDesc.of(owner, method.name(), methodTypeDesc)
                : ClassMethodDesc.of(owner, method.name(), methodTypeDesc);
    }

    public static ConstructorDesc constructorDescOf(MethodInfo ctor) {
        if (!ctor.isConstructor()) {
            throw new IllegalArgumentException("Cannot create ConstructorDesc for " + ctor);
        }

        List<ClassDesc> paramTypes = new ArrayList<>(ctor.parametersCount());
        for (int i = 0; i < ctor.parametersCount(); i++) {
            paramTypes.add(classDescOf(ctor.parameterType(i)));
        }
        return ConstructorDesc.of(classDescOf(ctor.declaringClass()), paramTypes);
    }
}
