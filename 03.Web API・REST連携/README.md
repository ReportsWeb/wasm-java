# 03. Web API・REST連携（Java / Spring Boot）

> このサンプルは Maven Central のパッケージ [`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web) と、帳票エンジンの Docker イメージ [`ghcr.io/reportsweb/engine`](https://github.com/ReportsWeb/engine) を使っています。

`api/` のJava帳票APIと `client/` の利用側Webアプリを別サービスとして動かし、HTTPでPREPEJを受け取り、同じデータを送り返してPDFを取得します。8種類の帳票に対応します。画像はPREPEJへ埋め込むため、APIプロキシ越しのPDFにも残ります。

## 1. 必要なもの

- このリポジトリを `git clone` したフォルダー（`../common/database/` を使います。帳票エンジンは Docker イメージ `ghcr.io/reportsweb/engine:1.1.0` から取得）
- Docker Desktop（Windows/macOS。WindowsはLinuxコンテナー）またはDocker Engine + Compose plugin（Linux）
- 初回のイメージ・Maven依存関係取得用インターネット接続

JDK/Mavenはコンテナー内に用意されます。`docker version` と `docker compose version` を確認してください。

## 2. 開いて起動する

Docker Desktop（Windows/macOS）またはDocker Engine + Compose plugin（Linux）を起動します。VS Code/Cursorでこの `03.Web API・REST連携` フォルダーを開き、統合ターミナルを開きます。Vimなどでも同フォルダーへ移動します。

```sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

`client`、`api`、`database`、`engine` が `Up`（healthy）なら [http://127.0.0.1:19212/](http://127.0.0.1:19212/) を開きます。「PREPEJを受け取る」で実際のREST応答とプレビューを確認し、保存、PDF取得、エラー応答を試します。Windowsは `.\Start.ps1`、macOS/Linuxは `sh start.sh` も利用できます。

## 3. 画面でREST連携を確かめる

1. 帳票を選び「PREPEJを受け取る」を押します。
2. HTTPステータス、Content-Type、サイズ、応答時間とプレビューを確認します。
3. 「PREPEJを保存」でAPI応答をファイル化します。
4. 「PDFを受け取る」で同じPREPEJをAPIへPOSTし、PDFを保存します。
5. 「エラー応答も確認」で存在しない帳票への安全なエラー応答を確認します。

`client` は利用側の学習画面を配信し、`/report-api` を内部の `api` サービスへ中継します。ブラウザーからJavaコンテナーを直接公開する構成ではありません。

## 4. 読む場所と変更例

- `client/index.html` — `/report-api` を呼ぶ利用側UI
- `client/nginx.conf.template` — Java APIとWASMプレビューへのプロキシ
- `api/sample-app/src/main/java/jp/pao/reports/sample/SampleController.java` — REST入口、PDF中継、画像埋め込み
- `api/sample-app/src/main/java/jp/pao/reports/sample/SampleCatalog.java` — DBから8帳票を作成
- Maven Central のパッケージ [`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web) — PREPEJ を組み立てる `PrintData` と、エンジンへ送る `ReportsWebEngine`
- `api/resources/definitions/`、`api/resources/images/` — 帳票定義と画像

`api/sample-app/src/main/java/jp/pao/reports/sample/SampleCatalog.java` の `invoice()` で、`.setValue("txtCustomer",s(h,"お客様名"))` を `.setValue("txtCustomer","サンプル商事")` に変更します。Javaは保存だけでは反映されないので、再コンパイルを伴う次のコマンドを実行します。

```sh
docker compose up -d --build --wait --wait-timeout 180
```

画面を再読み込みして請求書を受け取り、PREPEJとPDFで変更を確認します。ログは `docker compose logs -f api`、プロキシは `docker compose logs client` で確認できます。

Docker内Javaにブレークポイントを置くには、別途リモートデバッグ設定が必要です。このサンプルでは、まず再ビルドと `api` ログで確認します。ブラウザーJavaScriptはブラウザー開発者ツールで確認できます。

## 5. ログを確認する

`docker compose logs --tail=100`、`docker compose logs -f api`、`docker compose logs -f client` を使います。Ctrl+Cはログ表示だけを終了します。

## 6. 停止と削除

`docker compose stop` はDBデータを残す一時停止、`docker compose start` は再開です。`docker compose down --volumes` は03専用DBも削除します。イメージまで初期化する場合は次を使います。

```sh
docker compose down --rmi local --volumes --remove-orphans
docker compose ps -a
docker image ls reports-web-java-03-api
```

共有ファイルは `../common/database/` だけです。稼働サービスとDBは03専用で、上の削除は01・02や共有ファイルに影響しません。
最後の2一覧が見出しだけで対象行がなければ削除済みです。再度試すときは、第2節の起動コマンドから始めます。

## 7. 困ったとき

- SQLが見つからない／エンジンのイメージを取得できない: `../common/database/` があること、`docker pull `ghcr.io/reportsweb/engine:1.1.0`` が成功することを確認します。
- ポート19212が使用中: `.env` に `SAMPLE_PORT=19312` と書き、そのURLを開きます。
- REST応答がない: `client` と `api` の両ログ、`docker compose ps` のhealthを確認します。
- PDFだけ失敗する: `api` と `engine` のログを確認します。受信PREPEJが先に正常表示できるか切り分けます。
- DBを戻す: `docker compose down --volumes` 後に再起動します。
- ビルドを完全にやり直す: `docker compose build --no-cache` 後に起動します。

削除されるのは03のコンテナー、ネットワーク、DB、ローカルビルドイメージです。基本イメージとDockerビルドキャッシュは他用途と共有されるため残ります。
