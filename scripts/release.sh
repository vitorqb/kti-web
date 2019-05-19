#!/bin/bash
USAGE="\
$0 <tag>
Release script for kti-web.
Must be called with a tag as first argument. Creates the tag if needed.

MUST be called from the repository root.
ASSUMES that ./scripts/.release_env exists and exports the environmental
variable GITHUB_TOKEN, containing the github token that can access the
github web api.\
"

TAG="$1"
REPO_URL='https://api.github.com/repos/vitorqb/kti-web/'
JARFILE='./target/kti-web.jar'

source ./scripts/.release_env

function stopIfHelpFlagPassed() {
    if echo "$@" | grep -P '(^--help| --help)'
    then
        echo "$USAGE" >/dev/stderr
        exit 1
    fi
}
function ensureTagIsPassed() {
    echo "-> Checking tag..."
    [ -z "$TAG" ] && echo -e "ERROR: Missing tag\n$USAGE" >/dev/stderr && exit 1
    return 0
}
function ensureTagIsValid() {
    echo "-> Validating tag..."
    if ! echo "$TAG" | grep -q -P '^[0-9]{1,2}\.[0-9]{1,2}\.[0-9]{1,2}$'
    then
        echo "INVALID TAG FORMAT: USE x.x.x" >/dev/stderr && exit 1
    fi    
}
function ensureProjectCljHasTag() {
    echo "-> Checking tag on project.cljs..."
    expected="kti-web \"${TAG}\""
    if ! head ./project.clj -n1 | grep -q "$expected"
    then
        echo "Could not find '$expected' in project.cljs" >/dev/stderr  && exit 1
    fi
}
function ensureGithubToken() {
    if [ -z "$GITHUB_TOKEN" ]
    then
        echo "No github token specified.\n$USAGE" >/dev/stderr
        exit 1
    fi
}
function compile() {
    echo "-> Compiling..."
    lein do clean, uberjar
}
function maybeCreateTag() {
    if ! git tag | grep "$TAG" 
    then
        echo "-> Creating tag $TAG"
        git tag -a "$TAG" -m "$TAG"
    else
        echo "-> Tag already exists"
    fi
}
function pushTag() {
    echo "-> Pushing tag..."
    git push origin "$TAG"
}
function githubCurl() {
    curl -H "Authorization: TOKEN ${GITHUB_TOKEN}" "$@"
}
function createRelease() {
    echo "-> Creating release on github..."
    githubCurl --data @- "${REPO_URL}releases" <<EOF
{
  "tag_name": "$TAG",
  "name": "$TAG",
  "draft": false,
  "prerelease": false
}
EOF
}
function getUploadUrl() {
    githubCurl -s "${REPO_URL}releases/tags/$TAG" | jq --raw-output '.upload_url' | sed -r -e 's/\{.+\}//'
}
function uploadJar() {
    echo "-> Uploading jar..."
    URL="$(getUploadUrl)?name=kti-web.jar"
    githubCurl -H 'Content-Type: application/java-archive' --data-binary @$JARFILE $URL
}

stopIfHelpFlagPassed &&
    ensureTagIsPassed &&
    ensureTagIsValid &&
    ensureProjectCljHasTag &&
    ensureGithubToken &&
    compile &&
    maybeCreateTag &&
    pushTag &&
    createRelease &&
    uploadJar
