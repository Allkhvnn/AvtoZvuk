# Локальный production-запуск

## Первый запуск

1. Скопируйте `.env.example` в `.env` и задайте `DB_PASSWORD`.
2. Соберите приложение: `./gradlew.bat test bootJar --no-daemon`.
3. Проверьте запуск: `./scripts/start-production.ps1`.
4. Установите автозапуск: `./scripts/install-startup-task.ps1`.

Задача `AvtoZvuk Production Startup` запускается через 30 секунд после входа текущего пользователя в Windows. Скрипт:

- запускает Docker Desktop, если это необходимо;
- поднимает PostgreSQL и ожидает состояния `healthy`;
- не создаёт второй backend, если приложение уже работает;
- запускает production JAR на `http://localhost:8080/`;
- записывает логи в `build/runtime`.

Пароль находится только в локальном `.env`. Этот файл исключён из Git.

## Управление задачей

```powershell
Get-ScheduledTask -TaskName 'AvtoZvuk Production Startup'
Start-ScheduledTask -TaskName 'AvtoZvuk Production Startup'
Unregister-ScheduledTask -TaskName 'AvtoZvuk Production Startup'
```

После обновления кода сначала создайте новый production JAR. При следующем запуске задача использует новую сборку.

