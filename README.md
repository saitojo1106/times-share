# Times Share

Androidの共有シートから、X / Chrome / Qiita / GitHub などの共有テキスト・URLをDiscordの個人用チャンネルへ送る小さなAndroidアプリです。

## セットアップ

1. Android Studioでこのフォルダを開く
2. Gradle Syncを実行（初回はAndroid SDK・依存ライブラリのダウンロードが必要）
3. Android端末を接続してRun
4. インストールされた「Timesに保存」を一度起動
5. Discordで保存先チャンネルのWebhook URLを作成する
6. アプリで「送信先を追加」を選び、名前・Webhook URL・既存スレッドID（任意）を保存する
7. Xなどで「共有」→「Timesに保存」を選択し、送信先を選ぶ

## Discordの設定

1. 保存先のDiscordチャンネルで「チャンネルを編集」→「連携サービス」→「ウェブフック」を開く
2. 「新しいウェブフック」を作成し、保存先チャンネルを確認する
3. 「ウェブフックURLをコピー」を押す
4. アプリの設定画面へURLを貼り付けて保存する

Webhook URLは投稿権限を持つ秘密情報です。GitHub、チャット、スクリーンショットへ貼らないでください。漏れた場合はDiscord側でWebhookを再生成または削除してください。

## Webhookについて

Webhook URLはソースコードや環境変数には入れません。初回起動時に入力し、AndroidのSharedPreferencesへ保存します。そのためリポジトリへWebhookをコミットせずに使えます。

## 複数送信先

送信先は複数登録できます。各送信先に名前、Webhook URL、既存スレッドIDを保存し、共有時に送信先を選択します。既存スレッドIDを空欄にするとWebhookの標準チャンネルへ送信します。

## ビルド

Android Studio: Build > Build APK(s)

生成されたAPKは `app/build/outputs/apk/debug/app-debug.apk` に出力されます。

Gradle Wrapperを含んでいるため、CLIでは `./gradlew assembleDebug` でもビルドできます。

## GitHub Releases

`v1.0.0` のようなタグをpushすると、GitHub ActionsがDebug APKをビルドしてGitHub Releaseへ添付します。Actionsの手動実行にも対応しています。公開用の署名鍵はまだ設定していないため、Release APKはテスト・個人利用向けです。

## 動作確認

1. アプリを起動し、DiscordのWebhook URLを保存する
2. XまたはChromeでURLを共有する
3. 共有先から「Timesに保存」を選ぶ
4. Discordの指定チャンネルにURL・テキストが届くことを確認する

Webhook URLは端末のSharedPreferencesにのみ保存されます。GitHubへコミットしないでください。

スレッドIDも端末のSharedPreferencesにのみ保存されます。既存スレッドへ送る場合は、Discordで対象スレッドのメニューから「リンクをコピー」を選び、URL末尾の数字をアプリの「既存スレッドID」欄へ入力してください。

## 使い方

設定保存後、X・Chrome・Qiita・GitHubなどでURLやテキストを共有し、共有先の「Timesに保存」を選びます。アプリがDiscord Webhookへ送信し、成功すると「Timesに保存しました」と表示して終了します。

共有先に「Timesに保存」が表示されない場合は、アプリを一度起動して設定を保存し、共有内容がテキストとして渡せるアプリから試してください。
