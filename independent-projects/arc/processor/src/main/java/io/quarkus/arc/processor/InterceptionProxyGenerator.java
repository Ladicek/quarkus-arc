package io.quarkus.arc.processor;

import static io.quarkus.arc.processor.Jandex2Gizmo.classDescOf;
import static io.quarkus.arc.processor.Jandex2Gizmo.methodDescOf;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.interceptor.InvocationContext;

import org.jboss.jandex.AnnotationInstanceEquivalenceProxy;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.objectweb.asm.Opcodes;

import io.quarkus.arc.InjectableReferenceProvider;
import io.quarkus.arc.InterceptionProxy;
import io.quarkus.arc.InterceptionProxySubclass;
import io.quarkus.arc.impl.InterceptedMethodMetadata;
import io.quarkus.arc.processor.ResourceOutput.Resource;
import io.quarkus.gizmo.BytecodeCreator;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.FieldCreator;
import io.quarkus.gizmo.FieldDescriptor;
import io.quarkus.gizmo.FunctionCreator;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.gizmo2.Constant;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.Gizmo;
import io.quarkus.gizmo2.LocalVar;
import io.quarkus.gizmo2.ParamVar;
import io.quarkus.gizmo2.desc.ConstructorDesc;
import io.quarkus.gizmo2.desc.FieldDesc;
import io.quarkus.gizmo2.desc.MethodDesc;

public class InterceptionProxyGenerator extends AbstractGenerator {
    private static final String INTERCEPTION_SUBCLASS = "_InterceptionSubclass";

    private final Predicate<DotName> applicationClassPredicate;
    private final AnnotationLiteralProcessor annotationLiterals;
    private final ReflectionRegistration reflectionRegistration;

    InterceptionProxyGenerator(boolean generateSources, Predicate<DotName> applicationClassPredicate,
            AnnotationLiteralProcessor annotationLiterals, ReflectionRegistration reflectionRegistration) {
        super(generateSources);
        this.applicationClassPredicate = applicationClassPredicate;
        this.annotationLiterals = annotationLiterals;
        this.reflectionRegistration = reflectionRegistration;
    }

    Collection<Resource> generate(BeanInfo bean) {
        if (bean.getInterceptionProxy() == null) {
            return Collections.emptyList();
        }

        Function<String, Resource.SpecialType> specialTypeFunction = className -> {
            if (className.endsWith(INTERCEPTION_SUBCLASS)) {
                return Resource.SpecialType.SUBCLASS;
            }
            return null;
        };
        ResourceClassOutput classOutput = new ResourceClassOutput(applicationClassPredicate.test(bean.getBeanClass()),
                specialTypeFunction, generateSources);

        Gizmo gizmo = Gizmo.create(classOutput);

        createInterceptionProxyProvider(gizmo, bean);
        createInterceptionProxy(gizmo, bean);
        createInterceptionSubclass(gizmo, bean.getInterceptionProxy());

        return classOutput.getResources();
    }

    // ---

    static String interceptionProxyProviderName(BeanInfo bean) {
        return bean.getBeanClass().toString() + "_InterceptionProxyProvider_" + bean.getIdentifier();
    }

    private static String interceptionProxyName(BeanInfo bean) {
        return bean.getBeanClass().toString() + "_InterceptionProxy_" + bean.getIdentifier();
    }

    private static String interceptionSubclassName(InterceptionProxyInfo interceptionProxy) {
        return interceptionProxy.getTargetClass() + INTERCEPTION_SUBCLASS;
    }

    private void createInterceptionProxyProvider(Gizmo gizmo, BeanInfo bean) {
        gizmo.class_(interceptionProxyProviderName(bean), cc -> {
            cc.implements_(Supplier.class);
            cc.implements_(InjectableReferenceProvider.class);

            // Supplier
            cc.method("get", mc -> {
                mc.public_();
                mc.returning(Object.class);
                mc.body(bc -> bc.return_(cc.this_()));
            });

            // InjectableReferenceProvider
            cc.method("get", mc -> {
                mc.public_();
                mc.returning(Object.class);
                ParamVar creationalContext = mc.parameter("creationalContext", CreationalContext.class);
                mc.body(bc -> {
                    ConstructorDesc ctor = ConstructorDesc.of(ClassDesc.of(interceptionProxyName(bean)),
                            ClassDesc.of(CreationalContext.class.getName()));
                    bc.return_(bc.new_(ctor, creationalContext));
                });
            });
        });
    }

    private void createInterceptionProxy(Gizmo gizmo, BeanInfo bean) {
        gizmo.class_(interceptionProxyName(bean), cc -> {
            cc.implements_(InterceptionProxy.class);

            FieldDesc ccField = cc.field("creationalContext", fc -> {
                fc.private_();
                fc.final_();
                fc.withType(CreationalContext.class);
            });

            cc.constructor(mc -> {
                mc.public_();
                ParamVar ccParam = mc.parameter("creationalContext", CreationalContext.class);
                mc.body(bc -> {
                    bc.invokeSpecial(ConstructorDesc.of(Object.class), cc.this_());
                    bc.set(cc.this_().field(ccField), ccParam);
                    bc.return_();
                });
            });

            cc.method("create", mc -> {
                mc.public_();
                mc.returning(Object.class);
                ParamVar delegate = mc.parameter("delegate", Object.class);
                mc.body(b0 -> {
                    InterceptionProxyInfo interceptionProxy = bean.getInterceptionProxy();
                    b0.ifInstanceOf(delegate, classDescOf(interceptionProxy.getTargetClass()), (b1, ignored) -> {
                        ConstructorDesc ctor = ConstructorDesc.of(ClassDesc.of(interceptionSubclassName(interceptionProxy)),
                                CreationalContext.class, Object.class);
                        Expr ccVar = b1.get(cc.this_().field(ccField));
                        b1.return_(b1.new_(ctor, ccVar, delegate));
                    });

                    Expr err = b0.withNewStringBuilder()
                            .append("InterceptionProxy for ")
                            .append(bean.toString())
                            .append(" got unknown delegate: ")
                            .append(delegate)
                            .objToString();
                    b0.throw_(IllegalArgumentException.class, err);
                });
            });
        });
    }

    private void createInterceptionSubclass(Gizmo gizmo, InterceptionProxyInfo interceptionProxy) {
        BeanInfo pseudoBean = interceptionProxy.getPseudoBean();
        ClassInfo pseudoBeanClass = pseudoBean.getImplClazz();
        String pseudoBeanClassName = pseudoBeanClass.name().toString(); // TODO remove this
        boolean isInterface = pseudoBeanClass.isInterface();

        ClassDesc superClass = isInterface ? ConstantDescs.CD_Object : classDescOf(pseudoBeanClass);

        gizmo.class_(interceptionSubclassName(interceptionProxy), cc -> {
            cc.extends_(superClass);
            if (isInterface) {
                cc.implements_(classDescOf(pseudoBeanClass));
            }
            cc.implements_(InterceptionProxySubclass.class);

            FieldDesc delegateField = cc.field("delegate", fc -> {
                fc.private_();
                fc.final_();
                fc.withType(Object.class);
            });

            cc.field(SubclassGenerator.FIELD_NAME_CONSTRUCTED, fc -> {
                fc.private_();
                fc.final_();
                fc.withType(boolean.class);
            });

            IR ir = createIR(cc, pseudoBean);

            cc.constructor(mc -> {
                mc.public_();
                ParamVar ccParam = mc.parameter("creationalContext", CreationalContext.class);
                ParamVar delegateParam = mc.parameter("delegate", Object.class);
                mc.body(bc -> {
                    bc.invokeSpecial(ConstructorDesc.of(superClass), cc.this_());
                    bc.set(cc.this_().field(delegateField), delegateParam);

                    LocalVar arc = bc.define("arc", bc.invokeStatic(MethodDescs.ARC_CONTAINER));

                    Map<String, LocalVar> interceptorBeanToLocalVar = new HashMap<>();
                    Map<String, LocalVar> interceptorInstanceToLocalVar = new HashMap<>();
                    for (InterceptorInfo interceptorInfo : ir.boundInterceptors()) {
                        String id = interceptorInfo.getIdentifier();

                        LocalVar interceptorBean = bc.define("interceptorBean_" + id, bc.invokeInterface(
                                MethodDescs.ARC_CONTAINER_BEAN, arc, Constant.of(id)));
                        interceptorBeanToLocalVar.put(id, interceptorBean);

                        Expr ccChild = bc.invokeStatic(MethodDescs.CREATIONAL_CTX_CHILD, ccParam);
                        LocalVar interceptorInstance = bc.define("interceptorInstance_" + id, bc.invokeInterface(
                                MethodDescs.INJECTABLE_REF_PROVIDER_GET, interceptorBean, ccChild));
                        interceptorInstanceToLocalVar.put(id, interceptorInstance);
                    }

                    Map<MethodDesc, MethodDesc> forwardingMethods = new HashMap<>();
                    for (MethodInfo method : ir.interceptedMethods().keySet()) {
                        MethodDesc forwardDesc = SubclassGenerator.createForwardingMethod_2(cc, pseudoBeanClassName,
                                method, (bytecode, virtualMethod, params) -> {
                                    Expr delegate = bytecode.get(cc.this_().field(delegateField));
                                    return isInterface
                                            ? bytecode.invokeInterface(virtualMethod, delegate, params)
                                            : bytecode.invokeVirtual(virtualMethod, delegate, params);
                                });
                        forwardingMethods.put(methodDescOf(method), forwardDesc);
                    }

                    SubclassGenerator.IntegerHolder chainIdx = new SubclassGenerator.IntegerHolder();
                    SubclassGenerator.IntegerHolder bindingIdx = new SubclassGenerator.IntegerHolder();
                    Map<List<InterceptorInfo>, String> interceptorChainKeys = new HashMap<>();
                    Map<Set<AnnotationInstanceEquivalenceProxy>, String> bindingKeys = new HashMap<>();

                    LocalVar interceptorChainMap = bc.define("interceptorChainMap", bc.new_(ConstructorDesc.of(HashMap.class)));
                    LocalVar bindingsMap = bc.define("bindingsMap", bc.new_(ConstructorDesc.of(HashMap.class)));
                });
            });

            cc.method("arc_delegate", mc -> {
                mc.public_();
                mc.returning(Object.class);
                mc.body(bc -> {
                    bc.return_(cc.this_().field(delegateField));
                });
            });
        });

        try (ClassCreator clazz = ClassCreator.builder()
                .className(interceptionSubclassName(interceptionProxy))
                .build()) {

            FieldCreator delegate = clazz.getFieldCreator("delegate", Object.class)
                    .setModifiers(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);

            Map<String, ResultHandle> interceptorToResultHandle = new HashMap<>();
            Map<String, ResultHandle> interceptorInstanceToResultHandle = new HashMap<>();

            MethodCreator ctor = clazz.getConstructorCreator(CreationalContext.class, Object.class);
            ctor.invokeSpecialMethod(MethodDescriptor.ofConstructor(superClass), ctor.getThis());
            ctor.writeInstanceField(delegate.getFieldDescriptor(), ctor.getThis(), ctor.getMethodParam(1));
            ResultHandle arc = ctor.invokeStaticMethod(MethodDescriptors.ARC_CONTAINER);
            ResultHandle creationalContextHandle = ctor.getMethodParam(0);
            for (InterceptorInfo interceptorInfo : pseudoBean.getBoundInterceptors()) {
                ResultHandle interceptorBean = ctor.invokeInterfaceMethod(MethodDescriptors.ARC_CONTAINER_BEAN, arc,
                        ctor.load(interceptorInfo.getIdentifier()));
                interceptorToResultHandle.put(interceptorInfo.getIdentifier(), interceptorBean);

                ResultHandle creationalContext = ctor.invokeStaticMethod(MethodDescriptors.CREATIONAL_CTX_CHILD,
                        creationalContextHandle);
                ResultHandle interceptorInstance = ctor.invokeInterfaceMethod(MethodDescriptors.INJECTABLE_REF_PROVIDER_GET,
                        interceptorBean, creationalContext);
                interceptorInstanceToResultHandle.put(interceptorInfo.getIdentifier(), interceptorInstance);
            }

            Map<MethodDescriptor, MethodDescriptor> forwardingMethods = new HashMap<>();

            for (MethodInfo method : pseudoBean.getInterceptedMethods().keySet()) {
                forwardingMethods.put(MethodDescriptor.of(method), SubclassGenerator.createForwardingMethod(clazz,
                        pseudoBeanClassName, method, (bytecode, virtualMethod, params) -> {
                            ResultHandle delegateHandle = bytecode.readInstanceField(delegate.getFieldDescriptor(),
                                    bytecode.getThis());
                            return isInterface
                                    ? bytecode.invokeInterfaceMethod(virtualMethod, delegateHandle, params)
                                    : bytecode.invokeVirtualMethod(virtualMethod, delegateHandle, params);
                        }));
            }

            FieldCreator constructedField = clazz.getFieldCreator(SubclassGenerator.FIELD_NAME_CONSTRUCTED, boolean.class)
                    .setModifiers(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL);

            // Initialize maps of shared interceptor chains and interceptor bindings
            SubclassGenerator.IntegerHolder chainIdx = new SubclassGenerator.IntegerHolder();
            SubclassGenerator.IntegerHolder bindingIdx = new SubclassGenerator.IntegerHolder();
            Map<List<InterceptorInfo>, String> interceptorChainKeys = new HashMap<>();
            Map<Set<AnnotationInstanceEquivalenceProxy>, String> bindingKeys = new HashMap<>();

            ResultHandle interceptorChainMap = ctor.newInstance(MethodDescriptor.ofConstructor(HashMap.class));
            ResultHandle bindingsMap = ctor.newInstance(MethodDescriptor.ofConstructor(HashMap.class));

            // ============== the code below was not translated to Gizmo 2 yet ==============

            // Shared interceptor bindings literals
            Map<AnnotationInstanceEquivalenceProxy, ResultHandle> bindingsLiterals = new HashMap<>();
            Function<Set<AnnotationInstanceEquivalenceProxy>, String> bindingsFun = SubclassGenerator.createBindingsFun(
                    bindingIdx, ctor, bindingsMap, bindingsLiterals, pseudoBean, annotationLiterals);
            Function<List<InterceptorInfo>, String> interceptorChainKeysFun = SubclassGenerator.createInterceptorChainKeysFun(
                    chainIdx, ctor, interceptorChainMap, interceptorInstanceToResultHandle, interceptorToResultHandle);

            int methodIdx = 1;
            for (BeanInfo.InterceptionInfo interception : pseudoBean.getInterceptedMethods().values()) {
                // Each intercepted method has a corresponding InterceptedMethodMetadata field
                clazz.getFieldCreator("arc$" + methodIdx++, InterceptedMethodMetadata.class.getName())
                        .setModifiers(Opcodes.ACC_PRIVATE);
                interceptorChainKeys.computeIfAbsent(interception.interceptors, interceptorChainKeysFun);
                bindingKeys.computeIfAbsent(interception.bindingsEquivalenceProxies(), bindingsFun);
            }

            // Split initialization of InterceptedMethodMetadata into multiple methods
            int group = 0;
            int groupLimit = 30;
            MethodCreator initMetadataMethod = null;

            // to avoid repeatedly looking for the exact same thing in the maps
            Map<String, ResultHandle> chainHandles = new HashMap<>();
            Map<String, ResultHandle> bindingsHandles = new HashMap<>();

            methodIdx = 1;
            for (MethodInfo method : pseudoBean.getInterceptedMethods().keySet()) {
                if (initMetadataMethod == null || methodIdx >= (group * groupLimit)) {
                    if (initMetadataMethod != null) {
                        // End the bytecode of the current initMetadata method
                        initMetadataMethod.returnVoid();
                        initMetadataMethod.close();
                        // Invoke arc$initMetadataX(interceptorChainMap,bindingsMap) in the ctor method
                        ctor.invokeVirtualMethod(initMetadataMethod.getMethodDescriptor(), ctor.getThis(),
                                interceptorChainMap, bindingsMap);
                    }
                    initMetadataMethod = clazz.getMethodCreator("arc$initMetadata" + group++, void.class, Map.class, Map.class)
                            .setModifiers(Opcodes.ACC_PRIVATE);
                    chainHandles.clear();
                    bindingsHandles.clear();
                }

                MethodDescriptor methodDescriptor = MethodDescriptor.of(method);
                BeanInfo.InterceptionInfo interception = pseudoBean.getInterceptedMethods().get(method);
                MethodDescriptor forwardDescriptor = forwardingMethods.get(methodDescriptor);
                List<Type> parameters = method.parameterTypes();

                final MethodCreator initMetadataMethodFinal = initMetadataMethod;

                // 1. Interceptor chain
                String interceptorChainKey = interceptorChainKeys.get(interception.interceptors);
                ResultHandle chainHandle = chainHandles.computeIfAbsent(interceptorChainKey, ignored -> {
                    return initMetadataMethodFinal.invokeInterfaceMethod(MethodDescriptors.MAP_GET,
                            initMetadataMethodFinal.getMethodParam(0), initMetadataMethodFinal.load(interceptorChainKey));
                });

                // 2. Method method = Reflections.findMethod(org.jboss.weld.arc.test.interceptors.SimpleBean.class,"foo",java.lang.String.class)
                ResultHandle[] paramsHandles = new ResultHandle[3];
                paramsHandles[0] = initMetadataMethod.loadClass(pseudoBeanClassName);
                paramsHandles[1] = initMetadataMethod.load(method.name());
                if (!parameters.isEmpty()) {
                    ResultHandle paramsArray = initMetadataMethod.newArray(Class.class,
                            initMetadataMethod.load(parameters.size()));
                    for (ListIterator<Type> iterator = parameters.listIterator(); iterator.hasNext();) {
                        initMetadataMethod.writeArrayValue(paramsArray, iterator.nextIndex(),
                                initMetadataMethod.loadClass(iterator.next().name().toString()));
                    }
                    paramsHandles[2] = paramsArray;
                } else {
                    paramsHandles[2] = initMetadataMethod
                            .readStaticField(FieldDescriptors.ANNOTATION_LITERALS_EMPTY_CLASS_ARRAY);
                }
                ResultHandle methodHandle = initMetadataMethod.invokeStaticMethod(MethodDescriptors.REFLECTIONS_FIND_METHOD,
                        paramsHandles);

                // 3. Interceptor bindings
                // Note that we use a shared set if possible
                String bindingKey = bindingKeys.get(interception.bindingsEquivalenceProxies());
                ResultHandle bindingsHandle = bindingsHandles.computeIfAbsent(bindingKey, ignored -> {
                    return initMetadataMethodFinal.invokeInterfaceMethod(MethodDescriptors.MAP_GET,
                            initMetadataMethodFinal.getMethodParam(1), initMetadataMethodFinal.load(bindingKey));
                });

                // Instantiate the forwarding function
                // BiFunction<Object, InvocationContext, Object> forward = (target, ctx) -> target.foo$$superforward((java.lang.String)ctx.getParameters()[0])
                FunctionCreator func = initMetadataMethod.createFunction(BiFunction.class);
                BytecodeCreator funcBytecode = func.getBytecode();
                ResultHandle targetHandle = funcBytecode.getMethodParam(0);
                ResultHandle ctxHandle = funcBytecode.getMethodParam(1);
                ResultHandle[] superParamHandles;
                if (parameters.isEmpty()) {
                    superParamHandles = new ResultHandle[0];
                } else {
                    superParamHandles = new ResultHandle[parameters.size()];
                    ResultHandle ctxParamsHandle = funcBytecode.invokeInterfaceMethod(
                            MethodDescriptor.ofMethod(InvocationContext.class, "getParameters", Object[].class),
                            ctxHandle);
                    // autoboxing is handled inside Gizmo
                    for (int i = 0; i < superParamHandles.length; i++) {
                        superParamHandles[i] = funcBytecode.readArrayValue(ctxParamsHandle, i);
                    }
                }

                ResultHandle superResult = isInterface
                        ? funcBytecode.invokeInterfaceMethod(methodDescriptor, targetHandle, superParamHandles)
                        : funcBytecode.invokeVirtualMethod(methodDescriptor, targetHandle, superParamHandles);
                funcBytecode.returnValue(superResult != null ? superResult : funcBytecode.loadNull());

                ResultHandle aroundForwardFun = func.getInstance();

                // Now create metadata for the given intercepted method
                ResultHandle methodMetadataHandle = initMetadataMethod.newInstance(
                        MethodDescriptors.INTERCEPTED_METHOD_METADATA_CONSTRUCTOR,
                        chainHandle, methodHandle, bindingsHandle, aroundForwardFun);

                FieldDescriptor metadataField = FieldDescriptor.of(clazz.getClassName(), "arc$" + methodIdx++,
                        InterceptedMethodMetadata.class.getName());

                initMetadataMethod.writeInstanceField(metadataField, initMetadataMethod.getThis(), methodMetadataHandle);

                // Needed when running on native image
                reflectionRegistration.registerMethod(method);

                // Finally create the intercepted method
                SubclassGenerator.createInterceptedMethod(method, clazz, metadataField, constructedField.getFieldDescriptor(),
                        forwardDescriptor, bc -> bc.readInstanceField(delegate.getFieldDescriptor(), bc.getThis()));
            }

            if (initMetadataMethod != null) {
                // Make sure we end the bytecode of the last initMetadata method
                initMetadataMethod.returnVoid();
                // Invoke arc$initMetadataX(interceptorChainMap,bindingsMap) in the ctor
                ctor.invokeVirtualMethod(initMetadataMethod.getMethodDescriptor(), ctor.getThis(),
                        interceptorChainMap, bindingsMap);
            }

            ctor.writeInstanceField(constructedField.getFieldDescriptor(), ctor.getThis(), ctor.load(true));
            ctor.returnVoid();
        }
    }

    record InitMetadataMethodGroup(int id, List<MethodInfo> interceptedMethods) {
    }

    record IR(
            List<InterceptorInfo> boundInterceptors,
            Map<MethodInfo, BeanInfo.InterceptionInfo> interceptedMethods,
            List<InitMetadataMethodGroup> initMetadataGroups) {
    }

    private IR createIR(io.quarkus.gizmo2.creator.ClassCreator cc, BeanInfo pseudoBean) {
        List<InterceptorInfo> boundInterceptors = pseudoBean.getBoundInterceptors();

        Map<MethodInfo, BeanInfo.InterceptionInfo> interceptedMethods = pseudoBean.getInterceptedMethods();

        int groupLimit = 30;
        List<InitMetadataMethodGroup> initMetadataGroups = new ArrayList<>();
        List<MethodInfo> interceptedMethodsInGroup = new ArrayList<>();
        int groupCounter = 0;
        int methodCounter = 0;
        for (MethodInfo method : interceptedMethods.keySet()) {
            interceptedMethodsInGroup.add(method);
            methodCounter++;

            if (methodCounter == groupLimit) {
                initMetadataGroups.add(new InitMetadataMethodGroup(groupCounter, List.copyOf(interceptedMethodsInGroup)));

                groupCounter++;
                methodCounter = 0;
                interceptedMethodsInGroup.clear();
            }
        }
        if (!interceptedMethodsInGroup.isEmpty()) {
            initMetadataGroups.add(new InitMetadataMethodGroup(groupCounter, List.copyOf(interceptedMethodsInGroup)));
        }

        return new IR(boundInterceptors, interceptedMethods, List.copyOf(initMetadataGroups));
    }
}
