# Releasing

Every release must upload **four** assets. Two carry the version, two do not.

| asset | why |
| --- | --- |
| `hedgedoc-android-<version>-signed.apk` | provenance, permanent link to one build |
| `hedgedoc-android-<version>-unsigned.apk` | same |
| `hedgedoc-android-signed.apk` | what amni-scient.com/amni-hedgedoc links to |
| `hedgedoc-android-unsigned.apk` | same |

`https://github.com/Amnibro/hedgedoc-android/releases/latest/download/<name>` resolves against the
newest release and needs the **exact filename**. A site button pointing at a versioned name 404s the
moment the next release lands, so the site points at the unversioned pair and those must be uploaded
every time.

## Steps

```sh
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
V=1.2.0

gradle -p . :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --console=plain

mkdir -p dist
cp app/build/outputs/apk/release/app-release-unsigned.apk dist/hedgedoc-android-$V-unsigned.apk
cp app/build/outputs/apk/release/app-release-unsigned.apk dist/hedgedoc-android-$V-signed.apk

"$ANDROID_HOME/build-tools/35.0.0/apksigner.bat" sign \
  --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey dist/hedgedoc-android-$V-signed.apk

cp dist/hedgedoc-android-$V-signed.apk   dist/hedgedoc-android-signed.apk
cp dist/hedgedoc-android-$V-unsigned.apk dist/hedgedoc-android-unsigned.apk

git tag -a v$V -m "$V" && git push origin main v$V
gh release create v$V --title "$V" --notes-file notes.md dist/hedgedoc-android-*.apk
```

## Check before calling it done

The signing certificate must match every previous release or nobody can upgrade without
uninstalling first:

```sh
apksigner verify --print-certs dist/hedgedoc-android-signed.apk | grep SHA-256
# expect dee6b6a53a15ef273d8104908f5f6ffbef132bc9ebb1ce448a7cfab43c06e74c
```

Then confirm the live URLs, not the local files:

```sh
for n in hedgedoc-android-signed.apk hedgedoc-android-unsigned.apk; do
  curl -s -o /dev/null -w "$n %{http_code}\n" -L \
    "https://github.com/Amnibro/hedgedoc-android/releases/latest/download/$n"
done
```

Bump the version label on `amni-hedgedoc.html` and `about.html` in the amni-scient-site repo. Those
are text only; the download buttons no longer need touching.
