#!/usr/bin/env bash
# Gradle in a cloud chat, retried on network misses through the proxy (L175, L187, L196): Maven Central's 429s, the
# plugin portal's "was not found in any", and Robolectric's own android-all download ("Failed to fetch maven artifact",
# which only shows in the test results). --continue, so lint still runs when a test fails.
#   tools/gradle-retry.sh :app:testDebugUnitTest :app:lintRelease
# Prints each try, then the test count and lint errors; exits with Gradle's last exit code. The log is in
# android/build/gradle-retry.log (git-ignored).
set -uo pipefail
cd "$(dirname "$0")/../android" || exit 2
export ANDROID_HOME="${ANDROID_HOME:-$HOME/.pn-android/sdk}"
mkdir -p build
log=build/gradle-retry.log stamp=build/gradle-retry.stamp
net='429|Could not GET|was not found in any|Could not resolve|Failed to fetch maven artifact'
rc=1
for i in 1 2 3 4 5 6; do
  touch "$stamp"
  ./gradlew --no-daemon -q --continue "$@" > "$log" 2>&1; rc=$?
  echo "try $i: exit $rc"
  [ "$rc" -eq 0 ] && break
  # only this try's test results count: an older failure left on disk mustn't start another try
  fresh=$(find app/build/test-results -name '*.xml' -newer "$stamp" 2>/dev/null)
  if ! grep -qE "$net" "$log" && ! { [ -n "$fresh" ] && grep -ql "Failed to fetch maven artifact" $fresh; }; then break; fi
  # Maven Central's 429s can last for every try (L199): this machine only, never the repo, use Google's mirror of it
  mirror="$HOME/.gradle/init.d/central-mirror.gradle"
  if grep -q "429" "$log" && [ ! -f "$mirror" ]; then
    mkdir -p "$(dirname "$mirror")"
    cat > "$mirror" <<'GRADLE'
// Written by tools/gradle-retry.sh after a 429 (L199): Maven Central through Google's mirror, on this machine only.
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def swap = { repos ->
    repos.withType(MavenArtifactRepository).configureEach { r ->
        if (r.url.toString().startsWith('https://repo.maven.apache.org/maven2')) r.url = uri(mirror)
    }
}
beforeSettings { settings ->
    swap(settings.pluginManagement.repositories)
    swap(settings.dependencyResolutionManagement.repositories)
}
settingsEvaluated { settings ->
    swap(settings.pluginManagement.repositories)
    swap(settings.dependencyResolutionManagement.repositories)
}
allprojects {
    tasks.withType(Test).configureEach { systemProperty 'robolectric.dependency.repo.url', mirror }
}
GRADLE
    echo "Maven Central said 429: $mirror now points this machine at Google's mirror"
  fi
  [ "$i" -lt 6 ] && sleep $((20 * i))
done
[ "$rc" -eq 0 ] || sed -n '/What went wrong/,/^\* Try/p' "$log" | grep -v '^\* Try' | head -12
fresh=$(find app/build/test-results -name '*.xml' -newer "$stamp" 2>/dev/null)
[ -n "$fresh" ] && grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' $fresh \
  | awk -F'"' '{t+=$2;s+=$4;f+=$6;e+=$8} END {print "tests " t ", skipped " s ", failed " f ", errors " e}'
for x in app/build/reports/lint-results-*.xml; do
  [ "$x" -nt "$stamp" ] && echo "$(basename "$x" .xml): $(grep -c 'severity="Error"' "$x") errors"
done
exit "$rc"
