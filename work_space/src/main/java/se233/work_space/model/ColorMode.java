package se233.work_space.model;

//เป็น enum ที่บอกว่าผู้ใช้เลือกโหมดสีแบบไหน ตรงกับตัวเลือก Colors ในแผงขวาของโจทย์
//UNLIMITED	ใช้พาเล็ตสีเดิมของภาพ ไม่จำกัดจำนวนสี
//CUSTOM	จำกัดจำนวนสีของ SVG (ค่าเริ่มต้น 2 สี และสูงสุด 5 สี)
public enum ColorMode { UNLIMITED, CUSTOM }
