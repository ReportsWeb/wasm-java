# 02. 全帳票をWebでプレビュー（Java / Spring Boot）

> このサンプルは Maven Central のパッケージ [`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web) と、帳票エンジンの Docker イメージ [`ghcr.io/reportsweb/engine`](https://github.com/ReportsWeb/engine) を使っています。

請求書、見積書、郵便番号一覧など8種類の帳票を、JavaでDBから組み立ててWASMプレビューへ表示します。PREPEJの保存、定義のデザイナー表示、ブラウザー/サーバー双方のPDF生成も確認できます。

## 1. 必要なもの

- このリポジトリを `git clone` したフォルダー（`../common/database/` を使います。帳票エンジンは Docker イメージ `ghcr.io/reportsweb/engine:1.1.0` から取得）
- Docker Desktop（Windows/macOS。WindowsはLinuxコンテナー）またはDocker Engine + Compose plugin（Linux）
- 初回のイメージ・Maven依存関係取得用インターネット接続

ホスト側JDK/Mavenは不要です。`docker version` と `docker compose version` を確認してください。

## 2. 開いて起動する

Docker Desktop（Windows/macOS）またはDocker Engine + Compose plugin（Linux）を起動します。VS Code/Cursorでこの `02.全帳票をWebでプレビュー` フォルダーを開き、統合ターミナルを開きます。Vimなどでも、このフォルダーをカレントディレクトリにします。

```sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

`java`、`database`、`engine` が `Up`（healthy）なら [http://127.0.0.1:19211/](http://127.0.0.1:19211/) を開きます（帳票画面へ転送されます）。Windowsは `.\Start.ps1`、macOS/Linuxは `sh start.sh` も利用できます。

## 3. 画面で確かめる

帳票一覧を順に選び、8種類が表示されることを確認します。PREPEJの保存・再読込、「デザイナーで開く」、ブラウザーとサーバーを切り替えたPDF生成も試せます。保存したPREPEJは定義と必要な画像を含むため、後から再読込できます。

## 4. 読む場所と変更例

- `sample-app/src/main/java/jp/pao/reports/sample/SampleCatalog.java` — 8帳票のデータ取得・値設定
- `sample-app/src/main/java/jp/pao/reports/sample/SampleController.java` — HTTP入口、PREPEJ、PDF、画像埋め込み
- Maven Central のパッケージ [`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web) — 印刷データを組み立てる `PrintData` と、エンジンへ送る `ReportsWebEngine`
- `resources/definitions/`、`resources/images/` — 帳票定義と画像

たとえば `SampleCatalog.java` の `invoice()` で次の行を探します。

```java
.setValue("txtCustomer",s(h,"お客様名"))
```

右側を `.setValue("txtCustomer","サンプル商事")` に変更して保存します。Javaソースはビルド時コンパイルのため、自動反映されません。

```sh
docker compose up -d --build --wait --wait-timeout 180
```

画面を再読み込みして請求書を選び、プレビューとPDFで変更を確認します。ログは `docker compose logs -f java`、DBは `docker compose logs database` で確認できます。

Docker内Javaにブレークポイントを置くには、別途リモートデバッグ設定が必要です。このサンプルでは、まず編集→再ビルドとログで確認します。

## 5. ログを確認する

`docker compose logs --tail=100` で全体、`docker compose logs -f java` でSpring Bootを追えます。Ctrl+Cで止まるのはログ表示だけです。

## 6. 停止と削除

`docker compose stop` はDBデータを残す一時停止、`docker compose start` は再開です。`docker compose down --volumes` はこのサンプルのDBも削除します。イメージまで初期化する場合は次を使います。

```sh
docker compose down --rmi local --volumes --remove-orphans
docker compose ps -a
docker image ls reports-web-java-02-java
```

共有ファイルは `../common/database/` だけですが、稼働中のサービスとDBは02専用です。上の削除で共有ファイルや01・03は変更されません。
最後の2一覧が見出しだけで対象行がなければ削除済みです。再度試すときは、第2節の起動コマンドから始めます。

## 7. 困ったとき

- SQLが見つからない／エンジンのイメージを取得できない: `../common/database/` があること、`docker pull `ghcr.io/reportsweb/engine:1.1.0`` が成功することを確認します。
- ポート19211が使用中: `.env` に `SAMPLE_PORT=19311` と書き、そのポートで開きます。
- ビルドに失敗する: インターネット接続とログを確認し、必要なら `docker compose build --no-cache` の後で起動します。
- DBを初期状態へ戻す: `docker compose down --volumes` の後に起動します。
- 帳票が出ない: `docker compose ps` と `docker compose logs --tail=100` を先に確認し、healthyになる前にURLだけ開き直しても解決しません。

サンプル用イメージ、コンテナー、ネットワーク、DBボリュームは削除対象ですが、PostgreSQL/Node/Temurin等の基本イメージとビルドキャッシュは他用途と共有されるため残します。
