<p align="center"><img src="docs/images/banner.png" alt="Reports.Web for Java" width="100%"></p>

# Reports.Web — Java / Spring Boot サンプル

[![Maven Central](https://img.shields.io/maven-central/v/ac.pao/reports-web?label=Maven%20Central%20ac.pao%3Areports-web)](https://central.sonatype.com/artifact/ac.pao/reports-web)
[![engine](https://img.shields.io/badge/docker-ghcr.io%2Freportsweb%2Fengine-2563eb)](https://github.com/ReportsWeb/engine)
[![Live demo](https://img.shields.io/badge/demo-www.pao.ac-ffd166)](https://www.pao.ac/demo/reports.web/samples/java/)

**Reports.Web** は、ブラウザで帳票をデザインし、いろいろな言語から PDF を作る帳票ツールです。このリポジトリは、**Java（Spring Boot）** から使うサンプルです（製品の配布 ZIP に入っているサンプルと同じものです）。

請求書・見積書・納品書・宛名ラベル・名刺など、日本の業務帳票をそのまま作れます。帳票のレイアウトはブラウザのデザイナーで作り、アプリは値を入れて「印刷データ（PREPEJ）」を作るだけです。描画は C++ WebAssembly の帳票エンジンが行います。

- 製品サイト：https://www.pao.ac/reports.web/
- オンラインデモ：https://www.pao.ac/demo/reports.web/samples/java/
- Maven Central：[`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web)
- 帳票エンジン（Docker）：[`ghcr.io/reportsweb/engine`](https://github.com/ReportsWeb/engine)

| デザイナー（ブラウザ） | Java のサンプル（プレビュー） |
|---|---|
| <img src="docs/images/designer.png" alt="Reports.Web Designer"> | <img src="docs/images/java-demo.png" alt="Java sample"> |

## サンプルを選ぶ

| # | フォルダー | 内容 | ポート |
|:-:|---|---|---|
| 01 | [01.あっという間に帳票出力](01.あっという間に帳票出力/README.md) | Java の 1 ファイルで、1 ページの帳票を表示・PDF 出力 | http://127.0.0.1:19210/ |
| 02 | [02.全帳票をWebでプレビュー](02.全帳票をWebでプレビュー/README.md) | Spring Boot と PostgreSQL で 8 種類の帳票（請求書・見積書・郵便番号 81 ページなど）を作る Web アプリ | http://127.0.0.1:19211/ |
| 03 | [03.Web API・REST連携](03.Web%20API・REST連携/README.md) | 02 と同じ帳票処理を Web API にし、別の利用側 Web アプリから呼ぶ | http://127.0.0.1:19212/ |

迷ったら 01 から順にどうぞ。各フォルダーは独立していて、別々に起動・停止できます。

## クイックスタート

必要なのは Docker（Docker Desktop など）だけです。JDK・Maven・PostgreSQL をパソコンに入れる必要はありません。

```sh
git clone https://github.com/ReportsWeb/wasm-java.git
cd "wasm-java/01.あっという間に帳票出力"
docker compose up -d --build --wait --wait-timeout 180
# ブラウザで http://127.0.0.1:19210/ を開く
docker compose down --volumes   # 終わったら停止・削除
```

## コードの要点

```xml
<dependency>
  <groupId>ac.pao</groupId>
  <artifactId>reports-web</artifactId>
  <version>1.0.0</version>
</dependency>
```

```java
import jp.pao.reports.web.PrintData;
import jp.pao.reports.web.ReportsWebEngine;

PrintData printData = new PrintData()
    .setDefinition(definition)            // ブラウザのデザイナーで作った .prepdj（Jackson の JsonNode）
    .pageStart()
    .setValue("Message", "Javaから、あっという間に帳票出力")
    .pageEnd();

byte[] pdf = new ReportsWebEngine("http://engine:3107").renderPdf(printData);
```

Java 17 以上。依存は Jackson（jackson-databind）だけです。

## しくみ

```
Java アプリ ──(印刷データ PREPEJ / JSON)──▶ 帳票エンジン ghcr.io/reportsweb/engine ──▶ PDF
     ▲                                            （C++ WebAssembly、Docker）
 帳票定義 .prepdj（ブラウザのデザイナーで作成）
```

各サンプルの `compose.yaml` は、帳票エンジンのイメージを起動し、同じイメージからデザイナー・プレビュー画面のファイルを取り出して使います。

## 体験版と製品版

Maven のパッケージと帳票エンジンは**体験版**で、出力には赤い「SAMPLE」の印が付きます。ご購入後に納品するライセンスファイルを帳票エンジンに設定すると印が消えます（開発用パソコン 1 台につき 1 ライセンス、運用環境はランタイムライセンスフリー）。

- ご購入・お見積もり：https://www.pao.ac/reports.web/buy.html
- 使用許諾：https://www.pao.ac/reports.web/manual/index.html#16

このリポジトリのサンプルコードは [MIT License](LICENSE) です。Maven のパッケージと帳票エンジンには Reports.Web の使用許諾が適用されます。

## ほかの言語

同じ帳票定義・印刷データを、PHP・Java・.NET・Go・Ruby・Rust・Node.js から使えます。Pure Java・Pure Python・WPF 版もあります。→ https://github.com/ReportsWeb

`Manual/` のプログラマーズガイドは配布 ZIP 向けに書かれています。このリポジトリでは、`PrintData` を同梱ファイルではなく Maven Central のパッケージから読み込みます。

---

## English

**Reports.Web** is a report designer and PDF engine. Design report layouts (invoices, estimates, delivery slips, address labels, business cards) in the browser, then build print data (PREPEJ JSON) from your application and render PDF with the C++ WebAssembly engine. This repository contains the **Java / Spring Boot** samples shipped in the product ZIP.

- Package: [`ac.pao:reports-web`](https://central.sonatype.com/artifact/ac.pao/reports-web) (Maven Central, Java 17+)
- Engine: [`ghcr.io/reportsweb/engine`](https://github.com/ReportsWeb/engine) (Docker)
- Live demo: https://www.pao.ac/demo/reports.web/samples/java/

Quick start: install Docker, then

```sh
git clone https://github.com/ReportsWeb/wasm-java.git
cd "wasm-java/01.あっという間に帳票出力"
docker compose up -d --build --wait --wait-timeout 180   # open http://127.0.0.1:19210/
```

The package and the engine are trial builds; output carries a red "SAMPLE" mark until a purchased license file is configured. Sample code: MIT. Package and engine: Reports.Web license.

Pao@Office — https://www.pao.ac/ — info@pao.ac
