# نرخ‌چی

اپلیکیشن اندروید برای نمایش لحظه‌ای نرخ ارز، طلا، سکه و رمزارز به همراه ویجت صفحه‌ی اصلی قابل‌تنظیم.

## امکانات

- دریافت نرخ لحظه‌ای ارز، طلا، سکه و رمزارز از brsapi.ir
- انتخاب آزادانه‌ی آیتم‌های نمایشی توسط کاربر
- ویجت صفحه‌ی اصلی با طراحی مینیمال
- به‌روزرسانی خودکار پس‌زمینه در بازه‌ی معاملاتی (۱۱ تا ۱۷) هر یک ساعت
- فونت فارسی اختصاصی (Vazirmatn)

## تکنولوژی‌ها

- Kotlin, Jetpack Compose, Material3
- Jetpack Glance (ویجت)
- Retrofit + Gson
- WorkManager (به‌روزرسانی پس‌زمینه)
- DataStore (ذخیره‌سازی تنظیمات)

## ساخت پروژه

```
./gradlew assembleDebug
```

خروجی در مسیر `app/build/outputs/apk/debug/` قرار می‌گیرد. همچنین یک workflow در
`.github/workflows/build.yml` برای build خودکار از طریق GitHub Actions تعبیه شده است.

## ساختار پروژه

```
app/src/main/java/ir/pricewidget/app/
├── data/     مدل‌ها، سرویس API و مدیریت تنظیمات
├── ui/       صفحه‌ی اصلی و تم
├── widget/   ویجت Glance
└── work/     به‌روزرسانی پس‌زمینه
```
