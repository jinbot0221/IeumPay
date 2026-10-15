#!/usr/bin/env python3
"""Build using the toolchains retained in this cloud snapshot; no embedded credentials."""
import os, pathlib, subprocess, sys, urllib.parse
root = pathlib.Path('/workspace/toolchains')
jdk = root / 'jdk-17.0.14+7'
if not (jdk/'bin/javac').is_file():
    sys.exit('JDK missing: install JDK 17+ and use Android Studio/Gradle as documented in android/README.md')
os.environ['JAVA_HOME'] = str(jdk)
os.environ['GRADLE_USER_HOME'] = str(root/'gradle-home')
os.environ['ANDROID_USER_HOME'] = str(root/'android-user')
os.environ['ANDROID_HOME'] = str(root/'android')
args = [str(root/'gradle-8.11.1/bin/gradle'), '--no-daemon']
proxy = urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY', ''))
if proxy.hostname:
    for protocol in ('http', 'https'):
        args += [f'-D{protocol}.proxyHost={proxy.hostname}', f'-D{protocol}.proxyPort={proxy.port or 80}']
# Use the host-managed trust store, preserving certificate verification.
args += ['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
sys.exit(subprocess.call(args + (sys.argv[1:] or [':app:assembleDebug']), cwd=pathlib.Path(__file__).resolve().parent.parent/'android'))
