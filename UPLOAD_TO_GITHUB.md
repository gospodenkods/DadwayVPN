# Загрузка Dadway VPN 8.5.5 в GitHub

Рекомендуемый способ — работать с уже клонированным репозиторием, чтобы не создавать несвязанную историю Git.

## Обновление существующего репозитория

```powershell
git clone https://github.com/gospodenkods/DadwayVPN.git D:\git\DadwayVPN
```

Скопируйте в `D:\git\DadwayVPN` изменённые файлы проекта, но не удаляйте папку `.git`. Затем:

```powershell
cd D:\git\DadwayVPN
git status
git add --all
git commit -m "Release Dadway VPN 8.5.5"
git push origin main
```

После загрузки откройте **GitHub → Actions → Build Dadway VPN Production APK**. Скачайте артефакт `DadwayVPN-v8.5.5-production-apk`.
