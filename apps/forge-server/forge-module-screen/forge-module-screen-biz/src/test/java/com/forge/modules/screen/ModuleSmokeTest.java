package com.forge.modules.screen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleSmokeTest {

    @Test
    void module_package_path_is_correct() {
        assertEquals("com.forge.modules.screen",
                com.forge.modules.screen.ModuleSmokeTest.class.getPackageName());
    }
}
