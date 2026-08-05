import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import path from "node:path";

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const androidRoot = path.join(projectRoot, "android");
const variant = process.argv[2] === "release" ? "Release" : "Debug";
const wrapper = process.platform === "win32" ? "gradlew.bat" : "./gradlew";
const env = { ...process.env };

if (process.platform === "win32" && !env.ANDROID_HOME && !env.ANDROID_SDK_ROOT && env.LOCALAPPDATA) {
  env.ANDROID_HOME = path.join(env.LOCALAPPDATA, "Android", "Sdk");
}

const result = spawnSync(wrapper, [`assemble${variant}`], {
  cwd: androidRoot,
  env,
  stdio: "inherit",
  shell: process.platform === "win32",
});

if (result.error) {
  console.error(`Unable to start Android build: ${result.error.message}`);
  process.exit(1);
}

process.exit(result.status ?? 1);
