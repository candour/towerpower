package com.messark.hawkerrush

import android.app.Application
import io.mockk.mockk
import org.junit.Assert.assertNotNull
import org.junit.Test

class ViewModelFactoryTest {
    @Test
    fun `MainViewModel can be instantiated with only Application via reflection`() {
        val application = mockk<Application>(relaxed = true)
        val clazz = MainViewModel::class.java

        // This is what Android's default factory does
        val constructor = clazz.getConstructor(Application::class.java)
        val viewModel = constructor.newInstance(application)

        assertNotNull(viewModel)
    }
}
