package io.quarkus.arc.processor;

import static io.smallrye.common.constraint.Assert.impossibleSwitchCase;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ArrayType;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.PrimitiveType;
import org.jboss.jandex.Type;
import org.jboss.jandex.WildcardType;

import io.quarkus.gizmo2.GenericType;
import io.quarkus.gizmo2.TypeArgument;
import io.quarkus.gizmo2.creator.AnnotatableCreator;
import io.quarkus.gizmo2.creator.AnnotationCreator;
import io.quarkus.gizmo2.desc.ClassMethodDesc;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.InterfaceMethodDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

public class Jandex2Gizmo {
    private static final DotName CLASS_NAME = DotName.createSimple(Class.class);

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

    public static GenericType genericTypeOf(DotName name) {
        return GenericType.of(classDescOf(name));
    }

    public static GenericType genericTypeOf(Type type) {
        // TODO type annotations not yet supported
        return switch (type.kind()) {
            case VOID -> GenericType.of(void.class);
            case PRIMITIVE -> switch (type.asPrimitiveType().primitive()) {
                case BOOLEAN -> GenericType.of(boolean.class);
                case BYTE -> GenericType.of(byte.class);
                case SHORT -> GenericType.of(short.class);
                case INT -> GenericType.of(int.class);
                case LONG -> GenericType.of(long.class);
                case FLOAT -> GenericType.of(float.class);
                case DOUBLE -> GenericType.of(double.class);
                case CHAR -> GenericType.of(char.class);
            };
            case CLASS -> GenericType.of(classDescOf(type.name()));
            case PARAMETERIZED_TYPE -> {
                List<TypeArgument> typeArguments = type.asParameterizedType()
                        .arguments()
                        .stream()
                        .map(Jandex2Gizmo::typeArgumentOf)
                        .toList();
                yield GenericType.of(classDescOf(type.name()), typeArguments);
            }
            case ARRAY -> {
                GenericType array = genericTypeOf(type.asArrayType().elementType());
                for (int i = 0; i < type.asArrayType().deepDimensions(); i++) {
                    array = array.arrayType();
                }
                yield array;
            }
            case TYPE_VARIABLE, TYPE_VARIABLE_REFERENCE, UNRESOLVED_TYPE_VARIABLE -> {
                throw new UnsupportedOperationException("Type variables not yet supported");
            }
            case WILDCARD_TYPE -> {
                throw new IllegalArgumentException("Wildcard types may only be type arguments");
            }
        };
    }

    private static TypeArgument typeArgumentOf(Type type) {
        // TODO type annotations not yet supported
        return switch (type.kind()) {
            case VOID, PRIMITIVE -> {
                throw new IllegalArgumentException("Primitive types cannot be type arguments");
            }
            case CLASS, PARAMETERIZED_TYPE, ARRAY, TYPE_VARIABLE, TYPE_VARIABLE_REFERENCE, UNRESOLVED_TYPE_VARIABLE -> {
                yield TypeArgument.ofExact((GenericType.OfReference) genericTypeOf(type));
            }
            case WILDCARD_TYPE -> {
                WildcardType wildcard = type.asWildcardType();
                if (wildcard.superBound() != null) {
                    yield TypeArgument.ofSuper((GenericType.OfReference) genericTypeOf(wildcard.superBound()));
                } else if (wildcard.extendsBound() != null && (!DotName.OBJECT_NAME.equals(wildcard.extendsBound().name())
                        || !wildcard.extendsBound().annotations().isEmpty())) {
                    // the `extends` bound is either not `Object`, or it has type annotations and must be explicit
                    yield TypeArgument.ofExtends((GenericType.OfReference) genericTypeOf(wildcard.extendsBound()));
                } else {
                    yield TypeArgument.ofWildcard();
                }
            }
        };
    }

    public static Consumer<AnnotatableCreator> copyAnnotations(AnnotationTarget annotationTarget, IndexView index) {
        return creator -> {
            for (AnnotationInstance annotation : annotationTarget.annotations()) {
                addAnnotation(creator, annotation, index);
            }
        };
    }

    public static void addAnnotation(AnnotatableCreator annotatableCreator, AnnotationInstance annotation, IndexView index) {
        RetentionPolicy retention = annotation.runtimeVisible() ? RetentionPolicy.RUNTIME : RetentionPolicy.CLASS;
        annotatableCreator.addAnnotation(classDescOf(annotation.name()), retention, creatorFor(annotation, index));
    }

    private static Consumer<AnnotationCreator<Annotation>> creatorFor(AnnotationInstance annotation, IndexView index) {
        return creator -> {
            for (AnnotationValue member : annotation.values()) {
                switch (member.kind()) {
                    case BOOLEAN -> creator.add(member.name(), member.asBoolean());
                    case BYTE -> creator.add(member.name(), member.asByte());
                    case SHORT -> creator.add(member.name(), member.asShort());
                    case INTEGER -> creator.add(member.name(), member.asInt());
                    case LONG -> creator.add(member.name(), member.asLong());
                    case FLOAT -> creator.add(member.name(), member.asFloat());
                    case DOUBLE -> creator.add(member.name(), member.asDouble());
                    case CHARACTER -> creator.add(member.name(), member.asChar());
                    case STRING -> creator.add(member.name(), member.asString());
                    case CLASS -> creator.add(member.name(), classDescOf(member.asClass()));
                    case ENUM -> creator.add(member.name(), classDescOf(member.asEnumType()), member.asEnum());
                    case NESTED -> creator.add(member.name(), classDescOf(member.asNested().name()),
                            creatorFor(member.asNested(), index));
                    case ARRAY -> {
                        switch (member.componentKind()) {
                            case BOOLEAN -> creator.addArray(member.name(), member.asBooleanArray());
                            case BYTE -> creator.addArray(member.name(), member.asByteArray());
                            case SHORT -> creator.addArray(member.name(), member.asShortArray());
                            case INTEGER -> creator.addArray(member.name(), member.asIntArray());
                            case LONG -> creator.addArray(member.name(), member.asLongArray());
                            case FLOAT -> creator.addArray(member.name(), member.asFloatArray());
                            case DOUBLE -> creator.addArray(member.name(), member.asDoubleArray());
                            case CHARACTER -> creator.addArray(member.name(), member.asCharArray());
                            case STRING -> creator.addArray(member.name(), member.asStringArray());
                            case CLASS -> {
                                Type[] in = member.asClassArray();
                                ClassDesc[] out = new ClassDesc[in.length];
                                for (int i = 0; i < in.length; i++) {
                                    out[i] = classDescOf(in[i]);
                                }
                                creator.addArray(member.name(), out);
                            }
                            case ENUM -> {
                                DotName[] array = member.asEnumTypeArray();
                                assert array.length > 0;
                                ClassDesc enumType = classDescOf(array[0]);
                                creator.addArray(member.name(), enumType, member.asEnumArray());
                            }
                            case NESTED -> {
                                AnnotationInstance[] array = member.asNestedArray();
                                assert array.length > 0;
                                ClassDesc nestedType = classDescOf(array[0].name());
                                List<Consumer<AnnotationCreator<Annotation>>> creators = new ArrayList<>(array.length);
                                for (AnnotationInstance nested : array) {
                                    creators.add(creatorFor(nested, index));
                                }
                                creator.addArray(member.name(), nestedType, creators);
                            }
                            case UNKNOWN -> {
                                // empty array -- the only place where we need the `index`
                                ClassInfo annotationClass = index.getClassByName(annotation.name());
                                MethodInfo memberMethod = annotationClass.method(member.name());
                                assert memberMethod.returnType().kind() == Type.Kind.ARRAY;
                                Type type = memberMethod.returnType().asArrayType().elementType();
                                if (PrimitiveType.BOOLEAN.equals(type)) {
                                    creator.addArray(member.name(), new boolean[0]);
                                } else if (PrimitiveType.BYTE.equals(type)) {
                                    creator.addArray(member.name(), new byte[0]);
                                } else if (PrimitiveType.SHORT.equals(type)) {
                                    creator.addArray(member.name(), new short[0]);
                                } else if (PrimitiveType.INT.equals(type)) {
                                    creator.addArray(member.name(), new int[0]);
                                } else if (PrimitiveType.LONG.equals(type)) {
                                    creator.addArray(member.name(), new long[0]);
                                } else if (PrimitiveType.FLOAT.equals(type)) {
                                    creator.addArray(member.name(), new float[0]);
                                } else if (PrimitiveType.DOUBLE.equals(type)) {
                                    creator.addArray(member.name(), new double[0]);
                                } else if (PrimitiveType.CHAR.equals(type)) {
                                    creator.addArray(member.name(), new char[0]);
                                } else if (DotName.STRING_NAME.equals(type.name())) {
                                    creator.addArray(member.name(), new String[0]);
                                } else if (CLASS_NAME.equals(type.name())) {
                                    creator.addArray(member.name(), new Class[0]);
                                } else {
                                    assert type.kind() == Type.Kind.CLASS;
                                    ClassInfo clazz = index.getClassByName(type.name());
                                    if (clazz.isEnum()) {
                                        creator.addArray(member.name(), classDescOf(clazz), new String[0]);
                                    } else if (clazz.isAnnotation()) {
                                        creator.addArray(member.name(), classDescOf(clazz), List.of());
                                    } else {
                                        throw new IllegalArgumentException("Unknown type of empty array: "
                                                + memberMethod.returnType());
                                    }
                                }
                            }
                            default -> throw impossibleSwitchCase(member.kind());
                        }
                    }
                    default -> throw impossibleSwitchCase(member.kind());
                }
            }
        };
    }
}
