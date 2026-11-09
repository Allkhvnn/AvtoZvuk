# Деплой AvtoZvuk на VPS

## Требования

- Ubuntu/Debian VPS с Docker Engine и Docker Compose;
- домен с A-записью на публичный IP сервера;
- открытые входящие порты 80 и 443;
- закрытые извне 5432 и 8080.

Рекомендуемый минимум для одного магазина: 2 ГБ RAM, 2 CPU и 20 ГБ SSD.

## Первый запуск

```bash
git clone https://github.com/Allkhvnn/AvtoZvuk.git
cd AvtoZvuk
cp deploy/.env.production.example deploy/.env.production
nano deploy/.env.production
docker compose --env-file deploy/.env.production -f compose.production.yaml up -d --build
docker compose --env-file deploy/.env.production -f compose.production.yaml ps
```

Caddy автоматически получает и продлевает HTTPS-сертификат. DNS домена должен указывать на сервер до запуска.

`BOOTSTRAP_OWNER_PASSWORD` используется только при пустой базе. После первого входа смените пароль владельца через настройки. Для существующей базы bootstrap ничего не изменяет.

## Обновление

```bash
git pull --ff-only
docker compose --env-file deploy/.env.production -f compose.production.yaml up -d --build
```

PostgreSQL хранится в именованном volume и не удаляется при пересборке приложения. Не выполняйте `docker compose down -v`: параметр `-v` удаляет данные.

## Проверка

```bash
docker compose --env-file deploy/.env.production -f compose.production.yaml ps
docker compose --env-file deploy/.env.production -f compose.production.yaml logs --tail=100 app
curl -fsS https://YOUR_DOMAIN/api/health
```

Ожидаемый ответ healthcheck: `{"status":"UP"}`.

## Резервные копии

Контейнер `backup` создаёт архив при запуске и затем каждые 24 часа. Файлы находятся в `server-backups`, проверяются через `pg_restore` и хранятся 30 дней.

Папку `server-backups` обязательно нужно дополнительно синхронизировать во внешнее хранилище. Копия только на том же VPS не защищает от удаления или потери сервера.

Восстановление сначала выполняют на отдельной тестовой базе. Для рабочей базы остановите `app` и `backup`, создайте дополнительную копию текущих данных и только затем используйте `pg_restore`.

## Firewall

Пример для UFW:

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
```

Порты PostgreSQL и Spring Boot наружу не публикуются production Compose-файлом.

