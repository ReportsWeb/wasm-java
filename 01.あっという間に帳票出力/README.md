# 01. あっという間に帳票出力（Java）

> このサンプルは Maven Central のパッケージ [`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web) と、帳票エンジンの Docker イメージ [`ghcr.io/reportsweb/engine`](https://github.com/ReportsWeb/engine) を使っています。

JavaでPREPEJ印刷データを作り、ブラウザーのWASMプレビューとサーバー側WASM PDFを体験する最小サンプルです。DBやSpring Bootは使いません。

## デザインも変えてみましょう

このサンプルは、出力を見るだけでなく、デザイナーで文字・色・配置を自由に変え、プレビューとPDFに反映するところまで体験できます。

起動したら、画面上部の「デザイナーでデザインを変更」を押します。編集後に「変更を反映してプレビューへ戻る」を押すと、プレビューと「PDFを保存」の両方が編集後の内容になります。「変更せず戻る」なら変更を取り消せます。

この操作はブラウザー内の体験用です。プログラムが設定した文字も編集できるよう、現在の値をデザインへ入れて開きます。元の帳票ファイルやプログラムは上書きしません。ページを再読み込みすると元のサンプルに戻ります。編集結果を残す場合は、戻る前にデザイナーの保存機能で `.prepdj` を保存してください。ソースの値を変える練習とは別の操作です。

## 1. 必要なもの

- このリポジトリを `git clone` したフォルダー（帳票エンジンは Docker イメージ `ghcr.io/reportsweb/engine:1.0.0` から自動で取得します）
- Docker Desktop（Windows/macOS。WindowsはLinuxコンテナーへ切り替え）またはDocker Engine + Compose plugin（Linux）
- 初回にベースイメージを取得できるインターネット接続

JDKやMavenをホストへ入れる必要はありません。まず `docker version` と `docker compose version` が成功することを確認します。

## 2. 開いて起動する

Docker Desktop（Windows/macOS）またはDocker Engine + Compose plugin（Linux）を起動します。VS Code/Cursorなら「ファイル → フォルダーを開く」でこの `01.あっという間に帳票出力` を開き、ターミナルを開きます。Vimなどの場合もターミナルでこのフォルダーへ移動してください。

```sh
docker compose up -d --build --wait --wait-timeout 180
docker compose ps
```

`engine` と `quick-start` が `Up`（healthy）になったら [http://127.0.0.1:19210/](http://127.0.0.1:19210/) を開きます。帳票は自動で表示されます。上部の「PDFを保存」でPDFを確認できます。`QuickStartServer.java` が `/print-data` を返し、`/pdf` がWASMエンジンへPDF生成を依頼します。

Windowsは `./Start.ps1`、macOS/Linuxは `sh start.sh` でも起動できます。手動Composeコマンドが基本手順です。

## 3. 画面で確かめる

「Javaから、あっという間に帳票出力」と表示され、「PDFを保存」で同じ内容のPDFを保存できれば成功です。`report.prepdj` は帳票定義、`index.html` は操作画面、`QuickStartServer.java` は印刷データとHTTP処理です。印刷データは Maven Central のパッケージ `ac.pao:reports-web` の `PrintData` で作り、`ReportsWebEngine` でエンジンへ送ります（`pom.xml` はパッケージを取得するためだけに使います）。

## 4. ソースを変える

`QuickStartServer.java` の `createPrintData()` にある文字列「Javaから、あっという間に帳票出力」を探します。

```java
Javaから、あっという間に帳票出力
```

これを次の値へ変更して保存します。

```java
初めてのJava帳票
```

Javaはコンテナーのビルド時にコンパイルされるため、保存だけでは反映されません。次を実行して再コンパイル・再起動します。

```sh
docker compose up -d --build --wait --wait-timeout 180
```

ブラウザーを再読み込みし、変更した文字が自動で表示されることを確認します。ログは `docker compose logs -f quick-start`（Ctrl+Cはログ表示だけを終了）で確認できます。

Docker内Javaにブレークポイントを置くには、別途リモートデバッグ設定が必要です。このサンプルでは、まず編集→Docker再ビルド→画面確認の流れを利用してください。

## 5. ログを確認する

`docker compose logs --tail=100` で全体、`docker compose logs -f quick-start` でJava処理を追えます。Ctrl+Cはログ表示だけを終了し、サービスは停止しません。

## 6. 停止と削除

一時停止は `docker compose stop`、再開は `docker compose start` です。コンテナーとネットワークを削除するなら `docker compose down`（または `./Stop.ps1` / `./stop.sh`）です。サンプル用イメージも含めて初期化する場合は次を実行します。

```sh
docker compose down --rmi local --volumes --remove-orphans
docker compose ps -a
docker image ls reports-web-java-01-quick-start
```

最後の2一覧が見出しだけで対象行がなければ削除済みです。他サンプルは停止しません。再度試すときは、第2節の `docker compose up -d --build --wait --wait-timeout 180` から実行します。

## 7. 困ったとき

- `docker` が見つからない: Dockerを導入・起動し、新しいターミナルで確認します。
- エンジンのイメージを取得できない: インターネットに接続した状態で `docker pull `ghcr.io/reportsweb/engine:1.0.0`` が成功するか確認します。
- ポート19210が使用中: このフォルダーに `.env` を作り `SAMPLE_PORT=19310` と書き、表示URLもその番号にします。
- 画面が開かない: URLを先に開かず、`docker compose ps` がhealthyになるまで待ち、`docker compose logs --tail=100` を確認します。

macOS/Linuxで `./start.sh` に実行権限がない場合は `sh start.sh`、停止は `sh stop.sh` と実行できます。ベースイメージとビルドキャッシュは他のDocker作業でも再利用されるため、完全削除コマンドでも残る場合があります。
