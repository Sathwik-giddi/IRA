# IRA model assets

The GGUF weights are attached to the private `models-v1` release. They are not committed as Git objects because the combined size is larger than the free Git LFS allowance.

| File | Size | SHA-256 |
| --- | ---: | --- |
| `functiongemma-270m-it-Q4_K_M.gguf` | 253,127,904 bytes | `7474135cf63b5de86bd29d2feb92c644d48e9df5e1ac31550662af373c67d0fc` |
| `Qwen2.5-1.5B-Instruct-Q4_K_M.gguf` | 986,048,768 bytes | `1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370` |

## Download

```bash
gh release download models-v1 \
  -R Sathwik-giddi/IRA \
  -D models \
  --pattern '*.gguf'
```

Or:

```bash
./scripts/fetch-models.sh
```

## Install on a debug device

```bash
adb push models/functiongemma-270m-it-Q4_K_M.gguf /data/local/tmp/
adb push models/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf /data/local/tmp/

adb shell run-as com.voicepoco.app mkdir -p files/models
adb shell run-as com.voicepoco.app cp /data/local/tmp/functiongemma-270m-it-Q4_K_M.gguf files/models/
adb shell run-as com.voicepoco.app cp /data/local/tmp/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf files/models/

adb shell rm /data/local/tmp/functiongemma-270m-it-Q4_K_M.gguf
adb shell rm /data/local/tmp/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf
```

The app reads the files from its private `files/models` directory. A release build cannot use `run-as`, so bundle or download the weights through an approved production path.
