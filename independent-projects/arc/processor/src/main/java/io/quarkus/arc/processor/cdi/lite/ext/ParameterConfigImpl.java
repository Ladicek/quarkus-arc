package io.quarkus.arc.processor.cdi.lite.ext;

import javax.enterprise.inject.build.compatible.spi.ParameterConfig;
import javax.enterprise.lang.model.declarations.ParameterInfo;

class ParameterConfigImpl
        extends
        DeclarationConfigImpl<AnnotationsOverlay.Parameters.Key, org.jboss.jandex.MethodParameterInfo, ParameterConfigImpl>
        implements ParameterConfig {
    ParameterConfigImpl(org.jboss.jandex.IndexView jandexIndex, AllAnnotationTransformations allTransformations,
            org.jboss.jandex.MethodParameterInfo jandexDeclaration) {
        super(jandexIndex, allTransformations, allTransformations.parameters, jandexDeclaration);
    }

    @Override
    public ParameterInfo info() {
        return new ParameterInfoImpl(jandexIndex, allTransformations.annotationOverlays, jandexDeclaration);
    }
}
