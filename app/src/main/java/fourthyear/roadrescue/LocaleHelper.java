package fourthyear.roadrescue;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

public class LocaleHelper {

    public static void setLocale(String languageCode) {
        // 1. Create a LocaleListCompat with the new language
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(languageCode);

        // 2. Call AppCompatDelegate to set the new app locale
        AppCompatDelegate.setApplicationLocales(appLocale);
    }
}
