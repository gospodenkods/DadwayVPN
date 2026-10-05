# Подготовка Dadway VPN 8.6.4 для Google Play

Пакет: `ru.dadway.dadwayvpn`, версия: `8.6.4`, код: `865`, targetSdk: `36`.

## Рекомендации Console

1. Edge-to-edge: все три Activity включают `enableEdgeToEdge()` и применяют системные отступы. Общий обработчик защищает элементы от системных панелей, вырезов и клавиатуры, сохраняет исходный padding и запрашивает insets после прикрепления View к окну. Bottom sheet сохраняет обработку верхнего края Material, остальные края защищены общим обработчиком.
2. Устаревшие API: Console указала `Window.setStatusBarColor`, `Window.setNavigationBarColor`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` в обфусцированных классах `be.P`, `yd.P`, `zd.P`, `v.n`. В исходниках приложения прямых вызовов нет. Встроенный mapping предыдущего AAB `DadwayVPN-8.6.3-vc864.aab` подтверждает соответствие: `yd` — `androidx.activity.EdgeToEdgeApi23`, `zd` — `androidx.activity.EdgeToEdgeApi26`, `be` — `androidx.activity.EdgeToEdgeApi29`, `v` — `androidx.core.view.accessibility.AccessibilityNodeInfoCompat$$ExternalSyntheticApiModelOutline1` (синтетический класс R8 с вызовом через `WindowManager.LayoutParams` в методе `n`). Обновлены стабильные Activity, AppCompat и Core, CI запрещает возвращение этих параметров в исходники и сохраняет AAB mapping отдельно от последующей APK-сборки. Совместимые библиотеки могут содержать вызовы для старых Android; исчезновение предупреждения требуется подтвердить после загрузки нового AAB в Console.
3. Picture-in-Picture: рекомендация касается видеоплеера. Dadway VPN не воспроизводит видео; PiP не добавлен. Подключение отображается в foreground-уведомлении.

## Проверки сборки

CI запускает unit-тесты, Android Lint, R8 и resource shrinking, проверяет подпись, валидность AAB через bundletool, пакет, versionCode, versionName, targetSdk и обе ARM-архитектуры. Используется существующий release-ключ.

## Проверка на устройствах перед публикацией

Этот список описывает необходимые ручные проверки, а не выполненные результаты:

- Android 15/16: жестовая и трёхкнопочная навигация, светлая/тёмная тема.
- Главный экран, настройки, выбор приложений: поворот, вырез экрана, разделённое окно.
- Добавление подписки и поиск приложений: клавиатура не перекрывает поля и кнопки.
- Список серверов: первый и последний сервер доступны при прокрутке.
- Установка обновления поверх 8.6.3 из того же канала с сохранением подписок и настроек.

## Официальные источники

- [Edge-to-edge для Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [Изменения Android 15](https://developer.android.com/about/versions/15/behavior-changes-15)
- [AndroidX Activity: повторная настройка edge-to-edge при изменении конфигурации](https://developer.android.com/jetpack/androidx/releases/activity)
- [AndroidX AppCompat](https://developer.android.com/jetpack/androidx/releases/appcompat)
- [AndroidX Core](https://developer.android.com/jetpack/androidx/releases/core)
