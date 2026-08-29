import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";
import "./original-reference.css";
import "./home-editorial.css";
import "./home-research-insertions.css";
import "./full-redesign.css";
import "./full-redesign-mobile.css";
import "./global-polish.css";

createRoot(document.getElementById("root")!).render(<App />);
