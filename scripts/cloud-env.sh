# Source this file in the managed cloud shell. It changes no repository or global Java configuration.
export JAVA_HOME=/workspace/.tools/jdk-21.0.13+9
export GRADLE_USER_HOME=/workspace/.gradle
export npm_config_cache=/workspace/.npm
export PATH="$JAVA_HOME/bin:$PATH"
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    mapfile -t shirokai_proxy_parts < <(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlsplit(os.environ['HTTPS_PROXY'])
print(p.hostname or '')
print(p.port or 80)
PY
)
    if [[ -n "${shirokai_proxy_parts[0]}" ]]; then
        export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dhttps.proxyHost=${shirokai_proxy_parts[0]} -Dhttps.proxyPort=${shirokai_proxy_parts[1]} -Dhttp.proxyHost=${shirokai_proxy_parts[0]} -Dhttp.proxyPort=${shirokai_proxy_parts[1]} -Dhttp.nonProxyHosts=localhost|127.*"
    fi
    unset shirokai_proxy_parts
fi
