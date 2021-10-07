package io.quarkus.arc.test.cdi.lite.ext;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.arc.Arc;
import io.quarkus.arc.test.ArcTestContainer;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.inject.Instance;
import javax.enterprise.inject.build.compatible.spi.AnnotationBuilder;
import javax.enterprise.inject.build.compatible.spi.BeanInfo;
import javax.enterprise.inject.build.compatible.spi.BuildCompatibleExtension;
import javax.enterprise.inject.build.compatible.spi.Messages;
import javax.enterprise.inject.build.compatible.spi.Parameters;
import javax.enterprise.inject.build.compatible.spi.Registration;
import javax.enterprise.inject.build.compatible.spi.Synthesis;
import javax.enterprise.inject.build.compatible.spi.SyntheticBeanCreator;
import javax.enterprise.inject.build.compatible.spi.SyntheticBeanDisposer;
import javax.enterprise.inject.build.compatible.spi.SyntheticComponents;
import javax.enterprise.inject.build.compatible.spi.Types;
import javax.enterprise.inject.build.compatible.spi.Validation;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Singleton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

// TODO migrated to CDI TCK
public class SyntheticBeanTest {
    @RegisterExtension
    public ArcTestContainer container = ArcTestContainer.builder()
            .beanClasses(MyQualifier.class, MyService.class)
            .buildCompatibleExtensions(MyExtension.class)
            .build();

    @Test
    public void test() {
        MyService myService = Arc.container().select(MyService.class).get();
        assertEquals("Hello World", myService.unqualified.data);
        assertEquals("Hello @MyQualifier SynBean", myService.qualified.data);

        {
            MyComplexValue ann = MyPojoCreator.annotations.get("World");
            assertNotNull(ann);
            assertEquals(42, ann.number());
            assertEquals(MyEnum.YES, ann.enumeration());
            assertEquals(MyEnum.class, ann.type());
            assertEquals("yes", ann.nested().value());
            assertArrayEquals(new byte[] { 4, 5, 6 }, ann.nested().bytes());
        }

        {
            MyComplexValue ann = MyPojoCreator.annotations.get("SynBean");
            assertNotNull(ann);
            assertEquals(13, ann.number());
            assertEquals(MyEnum.NO, ann.enumeration());
            assertEquals(MyEnum.class, ann.type());
            assertEquals("no", ann.nested().value());
            assertArrayEquals(new byte[] { 1, 2, 3 }, ann.nested().bytes());
        }
    }

    public static class MyExtension implements BuildCompatibleExtension {
        private final List<BeanInfo> beans = new ArrayList<>();

        @Registration(types = MyPojo.class)
        public void rememberBeans(BeanInfo bean) {
            beans.add(bean);
        }

        @Synthesis
        public void synthesise(SyntheticComponents syn, Types types) {
            syn.addBean(MyPojo.class)
                    .type(MyPojo.class)
                    .withParam("data", AnnotationBuilder.of(MyComplexValue.class)
                            .member("number", 42)
                            .member("enumeration", MyEnum.YES)
                            .member("type", MyEnum.class)
                            .member("nested", new MySimpleValue.Literal("yes", new byte[] { 4, 5, 6 }))
                            .build())
                    .withParam("name", "World")
                    .createWith(MyPojoCreator.class)
                    .disposeWith(MyPojoDisposer.class);

            syn.addBean(MyPojo.class)
                    .type(MyPojo.class)
                    .qualifier(MyQualifier.class)
                    .withParam("data", AnnotationBuilder.of(MyComplexValue.class)
                            .member("number", 13)
                            .member("enumeration", MyEnum.class, "NO")
                            .member("type", types.ofClass("io.quarkus.arc.test.cdi.lite.ext.SyntheticBeanTest$MyEnum")
                                    .declaration())
                            .member("nested", AnnotationBuilder.of(
                                    types.ofClass("io.quarkus.arc.test.cdi.lite.ext.SyntheticBeanTest$MySimpleValue")
                                            .declaration())
                                    .value("no").build())
                            .build())
                    .withParam("name", "SynBean")
                    .createWith(MyPojoCreator.class)
                    .disposeWith(MyPojoDisposer.class);
        }

        @Validation
        public void validate(Messages messages) {
            for (BeanInfo bean : beans) {
                messages.info("bean has types " + bean.types(), bean);
            }
        }
    }

    // ---

    enum MyEnum {
        YES,
        NO,
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface MySimpleValue {
        String value();

        byte[] bytes() default { 1, 2, 3 };

        class Literal extends AnnotationLiteral<MySimpleValue> implements MySimpleValue {
            private final String value;
            private final byte[] bytes;

            Literal(String value) {
                this(value, new byte[] { 1, 2, 3 });
            }

            Literal(String value, byte[] bytes) {
                this.value = value;
                this.bytes = bytes;
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public byte[] bytes() {
                return bytes;
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface MyComplexValue {
        int number();

        MyEnum enumeration();

        Class<?> type();

        MySimpleValue nested();
    }

    // ---

    @Qualifier
    @Retention(RetentionPolicy.RUNTIME)
    @interface MyQualifier {
    }

    @Singleton
    static class MyService {
        @Inject
        MyPojo unqualified;

        @Inject
        @MyQualifier
        MyPojo qualified;
    }

    static class MyPojo {
        final String data;

        MyPojo(String data) {
            this.data = data;
        }
    }

    public static class MyPojoCreator implements SyntheticBeanCreator<MyPojo> {
        static final Map<String, MyComplexValue> annotations = new HashMap<>();

        @Override
        public MyPojo create(Instance<Object> lookup, Parameters params) {
            String name = params.get("name", String.class);

            annotations.put(name, params.get("data", MyComplexValue.class));

            InjectionPoint injectionPoint = lookup.select(InjectionPoint.class).get();
            if (injectionPoint.getQualifiers().stream().anyMatch(it -> it.annotationType().equals(MyQualifier.class))) {
                return new MyPojo("Hello @MyQualifier " + name);
            }

            return new MyPojo("Hello " + name);
        }
    }

    public static class MyPojoDisposer implements SyntheticBeanDisposer<MyPojo> {
        @Override
        public void dispose(MyPojo instance, Instance<Object> lookup, Parameters params) {
            System.out.println("disposing " + instance.data);
        }
    }
}
