# Personal Healthcare applications

個人の健康管理データベース登録アプリケーション
+ 血圧測定データの登録と可視化
+ スマートバンドからの睡眠スコア、活動量などのデータの登録と可視化
+ 夜間頻尿要因データの登録と解析

## 1. システム概要

現在ラズパイ4で実運用している気象センサーモニターシステムにFlask健康管理登録アプリを追加する

健康管理DBはPostgreSQL12でdocker-composeで追加で動作させる。

<div>
<img src="images/Raspi4_totalSystemOverView.png">
</div>
<br/>

既存システムについての詳細は下記リポジトリをご覧くださいへ  
https://github.com/pipito-yukio/raspi4_apps

## 2. 開発環境

Raspberry Pi 4 Model B が品薄状態のなか本番稼働している実機が1台しかないためシステムを追加する前にテスト用サーバー環境としてUbuntuワークステーションに下記のような開発環境を構築する。

<div>
<img src="images/DevelopmentEnviroment.png">
</div>
<br/>

### 2.1 サーバーWebアプリケーション (Flask2)

+ [ソースコード] src/webapp/

| 開発言語 | ライブラリ | version  | 用　途 |
|----------|-----------|--|--|
| Flask アプリ | python 仮想環境 | raspi4_apps |
|  | Flask | 2.1.3 | デバック用サーバー |
|  | waitres | 2.1.2 | 本番用WSGIサーバー |
|  | SQLAlchemy | 2.0以上 | ORマッパー ※Flask-SQLAlchemyは利用しない |
|  | psycopg2-binary | 2.9. | PostgreSQL用driver | 
|  | matplotlib | 3.5.2 | データ可視化 |
|  | pandas | 1.4.3 | Database読み込み |


### 2.2 バッチアプリケーション (Python)

WebアプリケーシにSQLAlchemyを使ったデータベース処理(処理クラス含む)を組み込む前にバッチ処理で検証する

+ [ソースコード] src/batch

| 開発言語 | ライブラリ | version  | 用　途 |
|----------|-----------|--|--|
| Python | python 仮想環境 | py_healthcare_tool |
|  | SQLAlchemy | 2.0以上 | ORマッパー |
|  | psycopg2-binary | 2.9. | PostgreSQL用driver | 
|  | openpyxl | 3.1.2| Excelシートからcsvファイルを生成 |
  


### 2.3 Androidアプリケーション

+ [ソースコード] src/android-health-care-example
+ 開発環境  
  Android Studio for Ubuntu 64bit

### 2.4 Docker-compose

+ [ソースコード] src/docker

### 2.5 データベース定義

+ [ソースコード] src/data/sql
