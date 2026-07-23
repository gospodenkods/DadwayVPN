# Загрузка Dadway VPN v7.3.3 в GitHub

1. Создайте на GitHub пустой **Private** репозиторий `DadwayVPN`.
2. Распакуйте архив, например в `D:\git\DadwayVPN-v7.3.3`.
3. Откройте PowerShell в каталоге проекта.
4. Снимите блокировку скачанного сценария:

```powershell
Unblock-File .\upload-to-github.ps1
```

5. Запустите загрузку:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File .\upload-to-github.ps1 `
  -RepositoryUrl "https://github.com/gospodenkods/DadwayVPN.git"
```

Версия 2.2 не использует `git rev-parse --verify HEAD` и другие проверочные команды, возвращающие код 1. Поэтому сценарий совместим с Windows PowerShell 5.1 и корректно работает в новом репозитории без первого commit.

После отправки откройте **GitHub → Actions → Build Dadway VPN v7.3.3 APK**. После успешной сборки скачайте артефакт `DadwayVPN-v7.3.3-apk`. При ошибке скачайте `DadwayVPN-v7.3.3-build-logs`.
