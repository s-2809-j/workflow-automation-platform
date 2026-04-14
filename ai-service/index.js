import "dotenv/config";

// 👉 Import your real app from src
import app from "./src/index.js";

const PORT = process.env.PORT || 3001;

// 🚀 Start server
app.listen(PORT, () => {
  console.log(`✅ AI Service running on http://localhost:${PORT}`);
});