# Загрузка Dadway VPN v8.2.0 в GitHub

Рекомендуемый способ — работать с уже клонированным репозиторием, чтобы не создавать несвязанную историю Git.

## Обновление существующего репозитория

```powershell
git clone https://github.com/gospodenkods/DadwayVPN.git D:\git\DadwayVPN
```

Скопируйте в `D:\git\DadwayVPN` всё содержимое папки версии 8.2.0 с заменой файлов, но не удаляйте папку `.git`. Затем:

```powershell
cd D:\git\DadwayVPN
git status
git add --all
git commit -m "Dadway VPN v8.2.0"
git push origin main
```

## Полная замена ветки main

Используйте только когда нужно заменить репозиторий содержимым распакованного архива:

```powershell
cd D:\git\DadwayVPN-v8.2.0
git init
git remote add origin https://github.com/gospodenkods/DadwayVPN.git
git add --all
git commit -m "Dadway VPN v8.2.0"
git branch -M main
git fetch origin
git push -u origin main --force-with-lease
```

Если до этого выполнялся `git pull --allow-unrelated-histories` и возникли конфликты:

```powershell
git merge --abort
git reset --hard HEAD
git fetch origin
git push -u origin main --force-with-lease
```

После загрузки откройте **GitHub → Actions → Build Dadway VPN v8.2.0 APK**. Скачайте артефакт `DadwayVPN-v8.2.0-apk`.
