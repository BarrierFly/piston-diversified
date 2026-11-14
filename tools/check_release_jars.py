#!/usr/bin/env python3
"""Release-jar self-check for Piston Diversified.

The 0.3.0 release jars shipped a stale `PistonHeadRendererMixin` whose `@ModifyArg` handler
still took `(BlockPos, BlockState)` — Mixin only accepts "only the modified argument" or "every
argument", so the whole target class failed to transform and the client crashed at first world
render, while the freshly compiled dev jar on the same machine was already correct. Incremental
builds cannot be trusted to keep `build/libs` in step with the sources, so before shipping:

    python tools/check_release_jars.py [version]        # default: the version in stonecutter.properties.toml

For every node jar in `build/libs/<version>/` this parses `PistonHeadRendererMixin.class` out of
the jar (a minimal class-file reader — the released jar is remapped to intermediary names, so
neither descriptor strings nor javap output are stable across mappings) and asserts:

  * the `pistonDiversified$moddedHeadState` handler exists exactly once, and
  * its parameter list is exactly one argument (the swapped block state).

Exit code 0 = all four jars clean, 1 = any problem.
"""
import os
import re
import struct
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)

NODES = ("1.19.4", "1.21.10", "1.21.11", "26.2")
JAR_DIRNAME = "26.2.x"
MIXIN_CLASS = "dev/zcode/piston_diversified/mixin/PistonHeadRendererMixin.class"
HANDLER = "pistonDiversified$moddedHeadState"


def read_version():
    with open(os.path.join(MOD, "stonecutter.properties.toml"), encoding="utf-8") as handle:
        match = re.search(r'^mod\.version\s*=\s*"([^"]+)"', handle.read(), re.M)
    if not match:
        raise SystemExit("could not read mod.version from stonecutter.properties.toml")
    return match.group(1)


def method_params(descriptor):
    """Parameter count of a method descriptor: the L…;/[ primitive tokens inside (...)"""
    params = descriptor[descriptor.index("(") + 1:descriptor.rindex(")")]
    count = 0
    index = 0
    while index < len(params):
        token = params[index]
        if token in "JD":  # long/double still count as one parameter
            index += 1
        elif token == "L":
            index = params.index(";", index) + 1
        elif token == "[":
            index += 1
            continue  # array prefix: the component type decides, do not count here
        else:
            index += 1
        count += 1
    return count


def parse_method_descriptors(class_bytes):
    """{method name: [descriptor, …]} straight from the class file's method table."""
    data = class_bytes
    assert data[:4] == b"\xca\xfe\xba\xbe", "not a class file"
    offset = 8  # magic + minor + major
    pool_count = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    strings = {}
    slot = 1
    while slot < pool_count:
        tag = data[offset]
        offset += 1
        if tag == 1:  # Utf8
            length = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            strings[slot] = data[offset:offset + length]
            offset += length
        elif tag in (3, 4):  # Integer / Float
            offset += 4
        elif tag in (5, 6):  # Long / Double — take two constant-pool slots
            offset += 8
            slot += 1
        elif tag in (7, 8, 16, 19, 20):  # Class / String / MethodType / Module / Package
            offset += 2
        elif tag in (9, 10, 11, 12, 17, 18):  # refs / NameAndType / Dynamic / InvokeDynamic
            offset += 4
        elif tag == 15:  # MethodHandle
            offset += 3
        else:
            raise AssertionError("unknown constant pool tag %d" % tag)
        slot += 1
    offset += 2  # access flags
    offset += 2  # this_class
    offset += 2  # super_class
    interface_count = struct.unpack_from(">H", data, offset)[0]
    offset += 2 + 2 * interface_count

    def skip_members(count):
        nonlocal offset
        members = []
        for _ in range(count):
            offset += 2  # access flags
            name_index, descriptor_index = struct.unpack_from(">HH", data, offset)
            offset += 4
            attribute_count = struct.unpack_from(">H", data, offset)[0]
            offset += 2
            for _ in range(attribute_count):
                offset += 2  # attribute_name_index
                length = struct.unpack_from(">I", data, offset)[0]
                offset += 4 + length
            members.append((strings.get(name_index), strings.get(descriptor_index)))
        return members

    field_count = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    skip_members(field_count)  # fields: parsed and discarded
    method_count = struct.unpack_from(">H", data, offset)[0]
    offset += 2
    methods = {}
    for name, descriptor in skip_members(method_count):
        methods.setdefault(name.decode("utf-8", "replace"), []).append(descriptor.decode("utf-8", "replace"))
    return methods


def check_jar(path):
    problems = []
    with zipfile.ZipFile(path) as jar:
        try:
            class_bytes = jar.read(MIXIN_CLASS)
        except KeyError:
            return ["%s missing from the jar" % MIXIN_CLASS]
    descriptors = parse_method_descriptors(class_bytes).get(HANDLER, [])
    if not descriptors:
        problems.append("handler %r not found" % HANDLER)
    elif len(descriptors) > 1:
        problems.append("%d overloads of %r (exactly one expected)" % (len(descriptors), HANDLER))
    else:
        params = method_params(descriptors[0])
        if params != 1:
            problems.append("%s takes %d parameters (mixin accepts only the swapped state, 1)"
                            % (HANDLER, params))
    return problems


def main():
    version = sys.argv[1] if len(sys.argv) > 1 else read_version()
    lib_dir = os.path.join(MOD, "build", "libs", version)
    if not os.path.isdir(lib_dir):
        print("no build/libs/%s — build the release first (buildAndCollect)" % version)
        return 1
    failed = False
    for node in NODES:
        name = "piston_diversified-%s+%s.jar" % (version, node)
        path = os.path.join(lib_dir, name)
        if not os.path.exists(path):
            print("MISSING %s" % name)
            failed = True
            continue
        problems = check_jar(path)
        if problems:
            failed = True
            for problem in problems:
                print("BAD    %s: %s" % (name, problem))
        else:
            print("OK     %s (%s takes 1 parameter)" % (name, HANDLER))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
