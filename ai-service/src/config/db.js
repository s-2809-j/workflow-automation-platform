import mongoose from "mongoose";

export async function connectDB() {
  const uri = process.env.MONGODB_URI;
  if (!uri) throw new Error("MONGODB_URI is missing in .env");

  mongoose.set("strictQuery", true);
  console.log("ENV CHECK - MONGODB_URI exists?", Boolean(process.env.MONGODB_URI));
  console.log("ENV CHECK - MONGODB_URI starts with:", (process.env.MONGODB_URI || "").slice(0, 25));
  
  await mongoose.connect(uri);

  console.log("✅ MongoDB connected");
}