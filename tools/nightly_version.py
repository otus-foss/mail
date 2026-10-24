"""Choose a nightly version using published provenance and every reserved release number."""

import json
import os
import re
import subprocess
from pathlib import Path


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def release_number(release):
    tag = release["tag_name"]
    match = re.fullmatch(r"nightly-([0-9]+)", tag)
    if match is None:
        raise ValueError(f"Unexpected release tag: {tag}")
    return int(match[1])


def previous_sources(repository, release):
    if release is None or not any(
        asset["name"] == "provenance.json" for asset in release["assets"]
    ):
        return "", ""
    provenance = json.loads(
        command(
            "gh",
            "release",
            "download",
            release["tag_name"],
            "-R",
            repository,
            "-p",
            "provenance.json",
            "-O",
            "-",
        )
    )
    sources = tuple(provenance[name] for name in ("mail", "meta"))
    if not all(isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{40}", sha) for sha in sources):
        raise ValueError("Invalid source revision in release provenance")
    return sources


def version_outputs(repository):
    mail_sha = command("git", "rev-parse", "HEAD")
    meta_sha = command("git", "-C", "otus-meta", "rev-parse", "HEAD")
    pages = json.loads(
        command("gh", "api", "--paginate", "--slurp", f"repos/{repository}/releases?per_page=100")
    )
    releases = sorted(
        (release for page in pages for release in page), key=release_number, reverse=True
    )
    published = next((release for release in releases if not release["draft"]), None)
    previous_mail, previous_meta = previous_sources(repository, published)
    outputs = {
        "sha": mail_sha,
        "meta_sha": meta_sha,
        "previous_mail": previous_mail,
        "previous_meta": previous_meta,
    }
    if (mail_sha, meta_sha) != (previous_mail, previous_meta):
        # Draft releases reserve their tags even when their assets were never published.
        number = (
            release_number(releases[0]) + 1
            if releases
            else int(command("git", "rev-list", "--count", "HEAD"))
        )
        outputs["number"] = str(number)
    return outputs


def main():
    outputs = version_outputs(os.environ["NIGHTLY_REPO"])
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as output:
        output.writelines(f"{key}={value}\n" for key, value in outputs.items())
    if "number" not in outputs:
        print(
            "::notice::The latest published nightly already contains these Mail and Meta revisions."
        )


if __name__ == "__main__":
    main()
