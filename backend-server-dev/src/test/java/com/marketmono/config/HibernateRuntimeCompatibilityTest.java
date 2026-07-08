package com.marketmono.config;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class HibernateRuntimeCompatibilityTest {

    @Test
    void shouldUseSpringBootCompatibleHibernateRuntime() {
        assertThat(Version.getVersionString()).startsWith("6.6.");
        assertThatCode(() -> Class.forName("org.hibernate.query.BindableType"))
            .doesNotThrowAnyException();
        assertThat(EntityManagerFactory.class.getPackage().getImplementationVersion()).startsWith("3.1.");
        assertThat(Arrays.stream(EntityManagerFactory.class.getMethods()).map(Method::getName))
            .doesNotContain("getSchemaManager");
    }
}
