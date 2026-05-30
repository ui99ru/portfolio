# Релизная подпись APK

Релизные сборки подписываются **отдельным ключом**, который **не хранится в
репозитории** — CI берёт его из GitHub Secrets, а локально (по желанию) из
git-ignored файла `keystore.properties`. Приватный ключ никогда не попадает в git.

> Debug-сборки по-прежнему подписываются committed-ключом `keystore/tinvest-lite.jks`
> (удобно для сайдлоада). Релиз — это распространяемый, доверенный вариант.

## 1. Сгенерировать релизный ключ (локально, один раз)

```bash
keytool -genkeypair -v \
  -keystore tinvest-lite-release.jks \
  -storetype PKCS12 \
  -alias tinvestlite-release \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=TInvest Lite, O=TInvestLite, C=RU"
```

`keytool` спросит пароль хранилища — запомни его (для PKCS12 пароль ключа = пароль
хранилища). **Сохрани `.jks` и пароль в надёжном месте** (менеджер паролей + бэкап):
без них нельзя будет выпускать обновления.

## 2. Закодировать ключ в base64

```bash
# Linux
base64 -w0 tinvest-lite-release.jks > keystore.b64
# macOS
base64 tinvest-lite-release.jks | tr -d '\n' > keystore.b64
```

## 3. Добавить секреты в GitHub

Settings → Secrets and variables → Actions → **New repository secret**:

| Secret                     | Значение                              |
|----------------------------|---------------------------------------|
| `SIGNING_KEYSTORE_BASE64`  | содержимое `keystore.b64`             |
| `SIGNING_STORE_PASSWORD`   | пароль хранилища                      |
| `SIGNING_KEY_ALIAS`        | `tinvestlite-release`                 |
| `SIGNING_KEY_PASSWORD`     | пароль ключа (= пароль хранилища)     |

После этого перезапусти workflow — артефакт `tinvest-lite-release` будет подписан
безопасным ключом. Пока секреты не добавлены, релиз собирается с откатом на
committed-ключ (CI не краснеет).

## 4. (Опционально) локальная релизная сборка

Создай в корне `keystore.properties` (он в `.gitignore`):

```properties
storeFile=/абсолютный/путь/tinvest-lite-release.jks
storePassword=...
keyAlias=tinvestlite-release
keyPassword=...
```

Затем: `./gradlew assembleRelease`.

## Миграция с debug на release

Релиз использует другой ключ и `applicationId` без суффикса `.debug`, поэтому при
первом переходе с сайдлоад-debug на release нужно **один раз удалить** debug-версию.
Дальше релизные обновления будут ставиться поверх друг друга без удаления.

## Если ключ скомпрометирован / потерян

Сгенерируй новый ключ, обнови секреты. Учти: пользователям придётся переустановить
приложение (подпись изменится). Поэтому ключ важно не терять.
