<p align="center">
  <img src="https://github.com/AieXile/AnimeTrack/blob/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp?raw=true" alt="AnimeTrack Logo" width="120" style="border-radius: 20px;"><br>
  <h1 align="center">AnimeTrack</h1>
  <p align="center">
    <i>Material Design 3 に準拠した Android 用アニメ追跡ツール —— 見たものは、すべて記憶に。</i>
  </p>
  <p align="center">
    <img src="https://api.visitorbadge.io/api/visitors?path=AieXile%2FAnimeTrack&label=Visitors&countColor=%23263759" alt="Visitors">
  </p>
  <p align="center">
    <img src="https://img.shields.io/github/stars/AieXile/AnimeTrack?style=flat&logo=github&label=Stars&color=yellow" alt="Stars">
    <img src="https://img.shields.io/github/v/release/AieXile/AnimeTrack?style=flat&logo=github&label=Version&color=blue" alt="Version">
    <img src="https://img.shields.io/badge/platform-Android-brightgreen?style=flat&logo=android&logoColor=white" alt="Platform">
    <img src="https://img.shields.io/badge/license-MIT-lightgrey?style=flat&logo=opensourceinitiative&logoColor=lightgrey" alt="License">
  </p>
  <p align="center">
    <a href="https://qun.qq.com/universal-share/share?ac=1&authKey=ToBlat%2BYBVr8R8J4kRqz5dZrwO08fUn1zJ47jsHDUmn04oxTLfipUhzDJNijY%2F3P&busi_data=eyJncm91cENvZGUiOiI5NTEwNTkxNzgiLCJ0b2tlbiI6IlgwRENkOGxubGFFOVd2cHZyRXNyQWsyU3VNb05DZ3ltNXpmUlg5T1NhQlh4emNoSDU1YnhWOWtUT2tFd1JLYlMiLCJ1aW4iOiIxMjE5NTc2NDA4In0%3D&data=f0HExdxeoQoLo-3m3KP-nlq9fIdMzKA3V5heiCbyagsnJqZRpLtjMq0yZ4W7BFNzDW9f17-YL24xP87SezjzCQ&svctype=4&tempid=h5_group_info"><img src="https://img.shields.io/badge/QQ-参加-blue?style=flat&logo=tencentqq&logoColor=white" alt="QQ Group"></a>
    <a href="https://t.me/AnimeTrackovo"><img src="https://img.shields.io/badge/TG-参加-2CA5E0?style=flat&logo=telegram&logoColor=white" alt="Telegram Group"></a>
  </p>
</p>

AnimeTrack は「何を見たか」「いつ見たか」を軸に設計された Android 向けアニメ追跡ツールで、検索・マーク・再生・振り返りまでの一連の流れを提供します。

- **マルチソース検索＆マーク** – Bangumi または TMDB からアニメを検索し、「見たい」「見てる」「見た」に素早くマーク。完了日時を自動記録。
- ~~**内蔵プレーヤー＆進捗連携** – ExoPlayer を統合し、WebDAV からローカルリソースを再生可能。視聴完了時に自動で話数更新、手動操作不要。~~
- **タイムライン＆ボード** – すべての視聴完了記録がタイムラインに集約され、補完履歴の振り返りが簡単に。連載中の作品は曜日別にボードに表示され、今日の更新がひと目でわかります。
- **マルチデバイスデータ同期** – 独自アカウント、Bangumi、Bilibili、WebDAV の4種類の同期方法に対応。クラウドバックアップ、機種変更、デバイス間シームレス移行で、データは常にあなたとともに。

---

<h2 align="center">ハイライト</h2>

<table align="center" width="100%">
  <tr>
    <td><b>デュアルソース検索</b> – Bangumi + TMDB 自動マッチング</td>
    <td><b>追跡ボード</b> – 今日の更新がひと目で</td>
  </tr>
  <tr>
    <td><b>マルチデバイス同期</b> – 独自アカウント / Bangumi / B站 / WebDAV</td>
    <td><b>タイムラインレポート</b> – あなたの補完軌跡をたどる</td>
  </tr>
  <tr>
    <td><b>Material You</b> – 動的カラー + 複数テーマプリセット</td>
    <td><b>Markdown 入出力</b> – データの自由な移行</td>
  </tr>
</table>

---

<h2 align="center">主な機能</h2>

<details>
<summary><b>クリックで全機能リストを展開</b></summary>

### 追跡＆再生
- **マルチデータソース検索** – Bangumi と TMDB の両方からの検索マッチングに対応し、カバー、話数、放送開始日などの基本情報を自動取得。データソースが欠けている作品は、詳細ページのマッチボタンから手動で補完可能（Bangumi または TMDB 別々にマッチング）。
- **複数ステータス管理** – アニメを「見たい」「見てる」「見た」の3ステータスに分類し、完全な追跡フローを実現。視聴中の作品は現在の視聴話数を記録でき、完了時に完了日時が自動書き込み。
- **複数シーズンコレクション** – 同一シリーズの複数シーズンを自動認識（「第X季/期/章」、ローマ数字、Final Season、最終季など多様な命名ルールに対応）。シリーズごとにグループ化し、カードスタック形式で表示。左右スワイプでシーズン間を切り替え可能、リスト内で探す手間が不要。
- ~~**内蔵プレーヤー** – ExoPlayer を統合し、WebDAV リモートディレクトリからローカルアニメリソースを直接再生。視聴進捗はローカル記録と連動し、視聴完了時に自動で話数更新。~~
- **タイムライン振り返り** – どのアニメをいつ見終わったかを時系列で確認でき、補完履歴の振り返りが簡単。月別ブラウズにも対応。
- **追跡ボード** – 追加した連載作品は毎週何曜日に更新されるかを表示し、ボード上で今日更新されるアニメを曜日別に一覧表示。ワンクリックで作品詳細へ移動。
- ~~**更新プッシュ通知** – WorkManager と JPush を利用し、更新日に通知。 (現在は非公開)~~

### 同期＆バックアップ
- **AnimeTrack アカウント同期** – 独自バックエンドに登録・ログインし、購読データをクラウド双方向同期。ログイン後は自動でクラウドデータを取得し、ローカルの追加・削除・変更もリアルタイムでアップロード。複数デバイス間でデータを統一。アバターアップロード、パスワード変更などのアカウント管理も可能。
- **Bangumi 同期** – Bangumi のコレクション状況と視聴進捗を双方向同期。ローカル記録を Bangumi お気に入りにプッシュしたり、Bangumi のマークをローカルにマージしたりでき、二重管理を回避。
- **Bilibili 同期** – B站の追跡リストをワンクリックで取得しローカルにマージ。B站アカウントにログイン後、同期したいアニメを選択すると、カバー・話数・ステータスなどを自動取得し、選択的インポートが可能。
- **WebDAV 同期** – WebDAV 経由でデータベースとカバーを自前のクラウド（Nutstore、Nextcloud など）にバックアップ。自動定期同期に対応し、データを自分の手元に保持。
- **Markdown インポート / エクスポート** – Markdown による一括インポート（ステータスグループ、話数情報、完了日、メモなどに対応。中英両方のキーワードを認識）をサポート。また、ローカル記録をタイムライン形式で可読性の高い Markdown ファイルにエクスポート可能。バックアップや他ツールへの移行に便利。
- **ZIP バックアップ / 復元** – ローカルデータベース（WAL ログ含む）とカバーディレクトリを ZIP にパッケージ。復元時は上書きとマージ（bangumiId またはタイトルで重複排除）の両モードに対応し、機種変更も安心。

### カスタマイズ＆ツール
- **Material Design 3** – Jetpack Compose ベースで構築。動的カラーとダークモードに対応し、M3 デザインガイドラインに準拠。
- **複数テーマプリセット** – クリアブルー、オーシャンシアン、ミントグリーン、スレートインディゴ、モノクロの5種類のカラースキームを内蔵。各テーマは異なるパレット戦略（TONAL_SPOT / VIBRANT / CONTENT / NEUTRAL）を採用し、異なるビジュアル印象を提供。
- **カスタマイズ可能なナビゲーションバー** – 従来の下部固定型とフローティングカプセル型の2種類のナビゲーションスタイルを提供。ナビゲーションエリアでの左右スワイプによるページ切り替えが可能で、選択インジケーターにはバネアニメーションが付随。
- **ガイドページ** – 初回インストール時のガイドで、コア機能と権限説明を素早く理解。
- **プロキシ設定** – Bangumi 逆プロキシ（Bangumi が一部地域でブロックされる問題を回避）とグローバル HTTP プロキシ（制限のあるネットワーク環境に対応）を内蔵。変更後は再起動で有効。
- **シェアカード** – カバー、タイトル、評価、進捗を含むアニメ情報カードを生成し、ワンタップで SNS にシェア。
- **カバー編集** – 詳細ページの編集モードでは、オンラインカバー検索、ギャラリーからのカスタムカバーアップロード、現在のカバーをローカルに保存の3機能を提供。ニーズに合わせたカスタマイズが可能。
- **データ統計** – アプリ使用時間、追加・完了したアニメ数を記録。日別 / 月別 / 年別で表示し、あなたの追跡足跡を数値化。
- **バージョン更新チェック** – GitHub Releases を通じて自動で新バージョンをチェック。バージョン番号を比較して更新を通知し、更新ログの閲覧も可能。

</details>

---

<h2 align="center">スクリーンショット</h2>

<h3 align="center">メイン画面</h3>
<table width="100%">
  <tr>
    <td width="50%" align="center">
      <img src="assets/Screenshot1.png" alt="メイン画面" style="width: 100%; border-radius: 12px;">
      <br><b>メイン画面</b>
    </td>
    <td width="50%" align="center">
      <img src="assets/Screenshot2.png" alt="タイムライン" style="width: 100%; border-radius: 12px;">
      <br><b>タイムライン</b>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="assets/Screenshot3.png" alt="ボード" style="width: 100%; border-radius: 12px;">
      <br><b>追跡ボード</b>
    </td>
    <td width="50%" align="center">
      <img src="assets/Screenshot4.png" alt="設定" style="width: 100%; border-radius: 12px;">
      <br><b>設定</b>
    </td>
  </tr>
</table>

<h3 align="center">その他の詳細</h3>
<table width="100%">
  <tr>
    <td width="33%" align="center">
      <img src="assets/Screenshot5.png" alt="カスタムナビゲーション" style="width: 100%; border-radius: 8px;">
      <br><b>カスタムナビゲーション</b>
    </td>
    <td width="33%" align="center">
      <img src="assets/Screenshot6.png" alt="機能UI" style="width: 100%; border-radius: 8px;">
      <br><b>機能UI</b>
    </td>
    <td width="33%" align="center">
      <img src="assets/Screenshot7.png" alt="MDインポート" style="width: 100%; border-radius: 8px;">
      <br><b>MDインポート</b>
    </td>
  </tr>
</table>

---

<h2>将来の計画：</h2>

- [x] **Bangumi アカウント同期** – ログイン後、クラウド記録を直接同期して紛失を防ぎ、双方向更新をサポート。
- [x] **視聴記録を MD にエクスポート** – ローカル記録をタイムライン形式で可読性の高い Markdown ファイルにエクスポート。バックアップや共有に便利。
- [ ] **タイムラインレポート** – 視聴履歴をもとに週報・月報・年報を自動生成し、シンプルなチャートで追跡トレンドを表示。
- [ ] **Web とアプリのマルチデバイス同期** – Web アクセスを提供し、Web とアプリ間でデータをリアルタイム同期。デバイス間のシームレスな連携を実現。
- [ ] **ローカル再生と自動記録** – ローカルプレーヤーを強化し、視聴進捗をタイムラインに自動記録。手動マークが不要に。

---

<h2 align="center">クイックスタート</h2>

### 環境要件
- Android 8.0 以上

### インストール
[Releases ページ](https://github.com/AieXile/AnimeTrack/releases) から最新の APK をダウンロードし、直接インストールしてください。

> 注意：現在はベータ版です。機能はまだ改善中です。問題が発生した場合は、お気軽に Issue を提出してください。

---

<h2 align="center">コントリビューション</h2>

[Issue](https://github.com/AieXile/AnimeTrack/issues) での問題報告や提案、また Fork して PR を送っていただくことも歓迎します。  
このプロジェクトが気に入ったら、Star をいただけると励みになります！

---

<h2 align="center">ライセンス</h2>

このプロジェクトは [MIT License](LICENSE) の下でオープンソース化されています。