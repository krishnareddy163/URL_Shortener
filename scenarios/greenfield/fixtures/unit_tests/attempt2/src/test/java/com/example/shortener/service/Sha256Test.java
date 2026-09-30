package com.example.shortener.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Sha256Test {

    @Test
    void digestsUtf8TextWithSha256() {
        assertThat(HexFormat.of().formatHex(Sha256.digest("abc")))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void anUnavailableAlgorithmIsReportedAsAnIllegalState() {
        assertThatThrownBy(() -> Sha256.digest("NO-SUCH-ALGORITHM", "abc"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("NO-SUCH-ALGORITHM unavailable");
    }

    @Test
    void theUtilityClassCanOnlyBeConstructedReflectively() throws ReflectiveOperationException {
        Constructor<Sha256> constructor = Sha256.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThat(constructor.newInstance()).isNotNull();
    }
}
