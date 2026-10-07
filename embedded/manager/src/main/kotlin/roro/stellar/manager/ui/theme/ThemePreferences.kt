// ==========================================================================

//   manager/src/main/kotlin/roro/stellar/manager/ui/theme/ThemePreferences.kt

//







// ==========================================================================

package roro.stellar.manager.ui.theme

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import roro.stellar.manager.R
import roro.stellar.manager.StellarSettings
import roro.stellar.manager.StellarSettings.COLOR_MODE
import roro.stellar.manager.StellarSettings.THEME_MODE

enum class ThemeMode(val value: String) {
    LIGHT("light"),
    DARK("dark"),
    AUTO("auto");

    companion object {
        fun fromValue(value: String): ThemeMode = entries.find { it.value == value } ?: AUTO
    }
}




enum class ColorMode(val value: String) {
    BRAND("brand"),
    DYNAMIC("dynamic");

    companion object {
        fun fromValue(value: String): ColorMode = entries.find { it.value == value } ?: BRAND
    }
}

enum class StartPage(val value: String) {
    HOME("home"),
    APPS("apps");

    
    
    

    companion object {
        fun fromValue(value: String): StartPage = entries.find { it.value == value } ?: HOME
    }
}

object ThemePreferences {

    private var _themeMode: MutableState<ThemeMode>? = null
    private var _startPage: MutableState<StartPage>? = null
    
    private var _colorMode: MutableState<ColorMode>? = null

    val themeMode: MutableState<ThemeMode>
        get() {
            if (_themeMode == null) {
                val savedValue = StellarSettings.getPreferences()
                    .getString(THEME_MODE, ThemeMode.AUTO.value) ?: ThemeMode.AUTO.value
                _themeMode = mutableStateOf(ThemeMode.fromValue(savedValue))
            }
            return _themeMode!!
        }

    
    val colorMode: MutableState<ColorMode>
        get() {
            if (_colorMode == null) {
                val savedValue = StellarSettings.getPreferences()
                    .getString(COLOR_MODE, ColorMode.BRAND.value) ?: ColorMode.BRAND.value
                _colorMode = mutableStateOf(ColorMode.fromValue(savedValue))
            }
            return _colorMode!!
        }

    val startPage: MutableState<StartPage>
        get() {
            if (_startPage == null) {
                val savedValue = StellarSettings.getPreferences()
                    .getString(StellarSettings.START_PAGE, StartPage.HOME.value) ?: StartPage.HOME.value
                _startPage = mutableStateOf(StartPage.fromValue(savedValue))
            }
            return _startPage!!
        }

    fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
        StellarSettings.getPreferences().edit {
            putString(THEME_MODE, mode.value)
        }
    }

    
    fun setColorMode(mode: ColorMode) {
        colorMode.value = mode
        StellarSettings.getPreferences().edit {
            putString(COLOR_MODE, mode.value)
        }
    }

    fun setStartPage(page: StartPage) {
        startPage.value = page
        StellarSettings.getPreferences().edit {
            putString(StellarSettings.START_PAGE, page.value)
        }
    }

    fun getThemeModeDisplayNameRes(mode: ThemeMode): Int = when (mode) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.AUTO -> R.string.theme_auto
    }

    
    fun getColorModeDisplayNameRes(mode: ColorMode): Int = when (mode) {
        ColorMode.BRAND -> R.string.color_mode_brand
        ColorMode.DYNAMIC -> R.string.color_mode_dynamic
    }

    
    fun getStartPageDisplayNameRes(page: StartPage): Int = when (page) {
        StartPage.HOME -> R.string.nav_home
        StartPage.APPS -> R.string.nav_apps
    }
}
