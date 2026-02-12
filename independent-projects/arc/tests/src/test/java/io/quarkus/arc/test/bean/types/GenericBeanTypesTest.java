package io.quarkus.arc.test.bean.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.arc.Arc;
import io.quarkus.arc.InjectableBean;
import io.quarkus.arc.test.ArcTestContainer;

public class GenericBeanTypesTest {
    @RegisterExtension
    ArcTestContainer container = new ArcTestContainer(MyBean.class, Producer.class, Alpha.class, Beta.class, Gamma.class,
            Delta.class, Epsilon.class, Zeta.class, Eta.class);

    @Test
    public void recursiveGeneric() {
        InjectableBean<Object> bean = Arc.container().instance("myBean").getBean();
        Set<Type> types = bean.getTypes();

        assertEquals(3, types.size());
        assertTrue(types.contains(Object.class));
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                Type genericClass = ((ParameterizedType) type).getRawType();
                assertTrue(MyBean.class.equals(genericClass) || Iterable.class.equals(genericClass));

                assertEquals(1, ((ParameterizedType) type).getActualTypeArguments().length);
                Type typeArg = ((ParameterizedType) type).getActualTypeArguments()[0];
                assertTrue(typeArg instanceof TypeVariable);
                assertEquals("T", ((TypeVariable<?>) typeArg).getName());

                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                Type bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertTrue(bound instanceof ParameterizedType);
                assertEquals(Comparable.class, ((ParameterizedType) bound).getRawType());

                assertEquals(1, ((ParameterizedType) bound).getActualTypeArguments().length);
                Type boundTypeArg = ((ParameterizedType) bound).getActualTypeArguments()[0];
                assertTrue(boundTypeArg instanceof TypeVariable);
                assertEquals("T", ((TypeVariable<?>) boundTypeArg).getName());
                // recursive
            }
        }
    }

    @Test
    public void duplicateRecursiveGeneric() {
        InjectableBean<Object> bean = Arc.container().instance("foobar").getBean();
        Set<Type> types = bean.getTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(Object.class));
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                Type genericClass = ((ParameterizedType) type).getRawType();
                assertEquals(FooBar.class, genericClass);

                assertEquals(2, ((ParameterizedType) type).getActualTypeArguments().length);

                Type typeArg = ((ParameterizedType) type).getActualTypeArguments()[0];
                assertTrue(typeArg instanceof TypeVariable);
                assertEquals("T", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                Type bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertTrue(bound instanceof ParameterizedType);
                assertEquals(FooBar.class, ((ParameterizedType) bound).getRawType());

                typeArg = ((ParameterizedType) type).getActualTypeArguments()[1];
                assertTrue(typeArg instanceof TypeVariable);
                assertEquals("U", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertTrue(bound instanceof ParameterizedType);
                assertEquals(Comparable.class, ((ParameterizedType) bound).getRawType());
            }
        }
    }

    @Test
    public void mutuallyRecursiveGeneric() {
        InjectableBean<Object> bean = Arc.container().instance("graph").getBean();
        Set<Type> types = bean.getTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains(Object.class));
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                Type genericClass = ((ParameterizedType) type).getRawType();
                assertEquals(Graph.class, genericClass);

                assertEquals(3, ((ParameterizedType) type).getActualTypeArguments().length);

                Type typeArg = ((ParameterizedType) type).getActualTypeArguments()[0];
                assertTrue(typeArg instanceof TypeVariable);
                assertEquals("G", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                Type bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertTrue(bound instanceof ParameterizedType);
                assertEquals(Graph.class, ((ParameterizedType) bound).getRawType());

                typeArg = ((ParameterizedType) type).getActualTypeArguments()[1];
                assertTrue(typeArg instanceof TypeVariable);
                assertEquals("E", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertTrue(bound instanceof ParameterizedType);
                assertEquals(Edge.class, ((ParameterizedType) bound).getRawType());

                typeArg = ((ParameterizedType) type).getActualTypeArguments()[2];
                assertTrue(typeArg instanceof TypeVariable);
                assertEquals("N", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertTrue(bound instanceof ParameterizedType);
                assertEquals(Node.class, ((ParameterizedType) bound).getRawType());
            }
        }
    }

    @Test
    public void typeVariables() {
        InjectableBean<Object> delta = Arc.container().instance("delta").getBean();
        Set<Type> deltaTypes = delta.getTypes();
        assertEquals(5, deltaTypes.size());
        assertTrue(deltaTypes.contains(Object.class));
        for (Type type : deltaTypes) {
            if (type instanceof ParameterizedType pt) {
                Type typeArg = pt.getActualTypeArguments()[0];
                assertInstanceOf(TypeVariable.class, typeArg);
                assertEquals("T", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                Type bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertEquals(Object.class, bound);
            }
        }

        InjectableBean<Object> epsilon = Arc.container().instance("epsilon").getBean();
        Set<Type> epsilonTypes = epsilon.getTypes();
        assertEquals(5, epsilonTypes.size());
        assertTrue(epsilonTypes.contains(Object.class));
        for (Type type : epsilonTypes) {
            if (type instanceof ParameterizedType pt) {
                Type typeArg = pt.getActualTypeArguments()[0];
                assertInstanceOf(TypeVariable.class, typeArg);
                assertEquals("U", ((TypeVariable<?>) typeArg).getName());
                assertEquals(1, ((TypeVariable<?>) typeArg).getBounds().length);
                Type bound = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertEquals(Number.class, bound);
            }
        }

        InjectableBean<Object> zeta = Arc.container().instance("zeta").getBean();
        Set<Type> zetaTypes = zeta.getTypes();
        assertEquals(5, zetaTypes.size());
        assertTrue(zetaTypes.contains(Object.class));
        for (Type type : zetaTypes) {
            if (type instanceof ParameterizedType pt) {
                Type typeArg = pt.getActualTypeArguments()[0];
                assertInstanceOf(TypeVariable.class, typeArg);
                assertEquals("V", ((TypeVariable<?>) typeArg).getName());
                assertEquals(2, ((TypeVariable<?>) typeArg).getBounds().length);
                Type bound0 = ((TypeVariable<?>) typeArg).getBounds()[0];
                assertEquals(Number.class, bound0);
                Type bound1 = ((TypeVariable<?>) typeArg).getBounds()[1];
                assertEquals(CharSequence.class, bound1);
            }
        }

        InjectableBean<Object> eta = Arc.container().instance("eta").getBean();
        Set<Type> etaTypes = eta.getTypes();
        assertEquals(5, etaTypes.size());
        assertTrue(etaTypes.contains(Object.class));
        for (Type type : etaTypes) {
            if (type instanceof ParameterizedType pt) {
                Type typeArg0 = pt.getActualTypeArguments()[0];
                assertInstanceOf(TypeVariable.class, typeArg0);
                assertEquals("W", ((TypeVariable<?>) typeArg0).getName());
                assertEquals(2, ((TypeVariable<?>) typeArg0).getBounds().length);
                Type bound0 = ((TypeVariable<?>) typeArg0).getBounds()[0];
                assertInstanceOf(ParameterizedType.class, bound0);
                assertEquals(Iterable.class, ((ParameterizedType) bound0).getRawType());
                Type bound0TypeArg = ((ParameterizedType) bound0).getActualTypeArguments()[0];
                assertInstanceOf(TypeVariable.class, bound0TypeArg);
                assertEquals("W", ((TypeVariable<?>) bound0TypeArg).getName());
                Type bound1 = ((TypeVariable<?>) typeArg0).getBounds()[1];
                assertInstanceOf(ParameterizedType.class, bound1);
                assertEquals(Map.class, ((ParameterizedType) bound1).getRawType());
                Type bound1TypeArg0 = ((ParameterizedType) bound1).getActualTypeArguments()[0];
                assertInstanceOf(TypeVariable.class, bound1TypeArg0);
                assertEquals("X", ((TypeVariable<?>) bound1TypeArg0).getName());
                Type bound1TypeArg1 = ((ParameterizedType) bound1).getActualTypeArguments()[1];
                assertInstanceOf(TypeVariable.class, bound1TypeArg1);
                assertEquals("Y", ((TypeVariable<?>) bound1TypeArg1).getName());

                if (pt.getActualTypeArguments().length > 1) {
                    assertEquals(3, pt.getActualTypeArguments().length);
                    Type typeArg1 = pt.getActualTypeArguments()[1];
                    assertInstanceOf(TypeVariable.class, typeArg1);
                    assertEquals("X", ((TypeVariable<?>) typeArg1).getName());
                    assertEquals(1, ((TypeVariable<?>) typeArg1).getBounds().length);
                    assertEquals(CharSequence.class, ((TypeVariable<?>) typeArg1).getBounds()[0]);
                    Type typeArg2 = pt.getActualTypeArguments()[2];
                    assertInstanceOf(TypeVariable.class, typeArg2);
                    assertEquals("Y", ((TypeVariable<?>) typeArg2).getName());
                    assertEquals(1, ((TypeVariable<?>) typeArg2).getBounds().length);
                    assertEquals(Number.class, ((TypeVariable<?>) typeArg2).getBounds()[0]);
                }
            }
        }
    }

    @Dependent
    @Named("myBean")
    static class MyBean<T extends Comparable<T>> implements Iterable<T> {
        @Override
        public Iterator<T> iterator() {
            return null;
        }
    }

    @Singleton
    static class Producer {
        @Produces
        @Dependent
        @Named("foobar")
        <T extends FooBar<T, U>, U extends Comparable<U>> FooBar<T, U> produceFooBar() {
            return new FooBar<>() {
            };
        }

        @Produces
        @Dependent
        @Named("graph")
        <G extends Graph<G, E, N>, E extends Edge<G, E, N>, N extends Node<G, E, N>> Graph<G, E, N> produceGraph() {
            return new Graph<>() {
            };
        }
    }

    interface FooBar<T extends FooBar<?, U>, U extends Comparable<U>> {
    }

    interface Graph<G extends Graph<G, E, N>, E extends Edge<G, E, N>, N extends Node<G, E, N>> {
    }

    interface Edge<G extends Graph<G, E, N>, E extends Edge<G, E, N>, N extends Node<G, E, N>> {
    }

    interface Node<G extends Graph<G, E, N>, E extends Edge<G, E, N>, N extends Node<G, E, N>> {
    }

    interface Alpha<A> {
    }

    interface Beta<B> extends Alpha<B> {
    }

    interface Gamma<G> extends Beta<G> {
    }

    @Dependent
    @Named("delta")
    static class Delta<T> implements Gamma<T> {
    }

    @Dependent
    @Named("epsilon")
    static class Epsilon<U extends Number> implements Gamma<U> {
    }

    @Dependent
    @Named("zeta")
    static class Zeta<V extends Number & CharSequence> implements Gamma<V> {
    }

    @Dependent
    @Named("eta")
    static class Eta<W extends Iterable<W> & Map<X, Y>, X extends CharSequence, Y extends Number> implements Gamma<W> {
    }
}
