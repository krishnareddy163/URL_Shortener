package com.example.shortener.service;

import org.junit.jupiter.api.Test;

import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class CodeGeneratorTest {

    @Test
    void generatesSevenCharacterCodes() {
        assertThat(new CodeGenerator().generate()).hasSize(8);
    }

    @Test
    void alphabetIsBase62WithoutDuplicates() {
        assertThat(CodeGenerator.ALPHABET).hasSize(62);
        assertThat(CodeGenerator.ALPHABET.chars().distinct().count()).isEqualTo(62);
    }

    @Test
    void generatedCodesUseOnlyTheBase62Alphabet() {
        CodeGenerator generator = new CodeGenerator();
        for (int sample = 0; sample < 1_000; sample++) {
            assertThat(generator.generate()).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    void mapsRandomIndexesOntoTheAlphabet() {
        int[] indexes = {0, 61, 10, 35, 36, 1, 9};
        RandomGenerator scripted = new RandomGenerator() {
            private int next;

            @Override
            public long nextLong() {
                return indexes[next++];
            }

            @Override
            public int nextInt(int bound) {
                return indexes[next++];
            }
        };

        assertThat(new CodeGenerator(scripted).generate()).isEqualTo("0zAZa19");
    }
}
