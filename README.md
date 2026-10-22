# GitHub Upload Helper Android — v2.0

แอป Android สำหรับเลือก ZIP แล้วอัปโหลดไฟล์ไปยัง GitHub Repository ผ่าน GitHub REST API โดยตรง

## ความสามารถ v2.0
- แท็บตั้งค่า Repository: Token, Owner, Repository, Branch และตรวจสอบการเข้าถึง
- บันทึก Owner / Repository / Branch ในเครื่อง แต่ไม่บันทึก Token ลง SharedPreferences
- ทางลัดเปิด Repository, Code, Commits, Repository Settings, Releases และหน้าจัดการ/สร้าง Personal Access Token
- อัปโหลดไฟล์ใน ZIP
- ตัวเลือกอนุญาตเขียนทับไฟล์เดิม โดยอ่าน SHA ของไฟล์เดิมก่อนเรียก API
- โหมดลบ: ใช้ path จาก ZIP เป็นรายการเป้าหมาย ลบไฟล์ตรงกันหลังยืนยัน และสร้าง commit จริง
- จำกัด 500 ไฟล์ และขนาดรวมหลังแตก ZIP 150 MB; ปฏิเสธ path ที่ไม่ปลอดภัย

## Token permissions
สำหรับ Fine-grained personal access token ให้เลือก Repository ที่ต้องการ และตั้ง `Contents: Read and write`. หากแก้ไขไฟล์ใน `.github/workflows` ให้ตั้ง `Workflows: Read and write` ด้วย ตรวจสิทธิ์เบื้องต้นจาก API ไม่สามารถรับประกันผลของ Branch protection หรือข้อกำหนดขององค์กรได้ การเขียนทับ/ลบจะตรวจผลตอบกลับจริงจาก API

## Build
- JDK 17
- Gradle 8.9
- Android SDK compileSdk 35
- คำสั่ง: `gradle assembleDebug`
- ไฟล์ APK: `app/build/outputs/apk/debug/app-debug.apk`

APK debug เหมาะสำหรับทดสอบ ไม่ใช่ release signing สำหรับ Play Store. Token ใช้ส่งตรงไป `api.github.com` ผ่าน HTTPS; ห้ามแชร์ Token และเพิกถอนทันทีหากสงสัยว่าหลุด
