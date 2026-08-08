Import("env")

from os.path import isdir, join


def verify_esp32c3_multilib(source, target, env):
    map_path = join(
        env.subst("$BUILD_DIR"),
        f"{env.subst('$PROGNAME')}.map",
    )
    with open(map_path, encoding="utf-8", errors="replace") as map_file:
        link_map = map_file.read().replace("\\", "/")

    required_atomicity_path = (
        "rv32imc_zicsr_zifencei/ilp32/libstdc++.a(atomicity.o)"
    )
    if required_atomicity_path not in link_map:
        raise RuntimeError(
            "ESP32-C3 build linked the wrong libstdc++ multilib. "
            f"Expected {required_atomicity_path} in {map_path}."
        )
    print("Verified ESP32-C3 RV32IMC libstdc++ multilib")


# PlatformIO applies project build flags while compiling, but its Arduino link
# action does not automatically carry plain -march/-mabi flags across. Keep the
# final link on the ESP32-C3's RV32IMC multilib as well; the toolchain default is
# RV32IMAC and emits unsupported AMO instructions in libstdc++.
if env.BoardConfig().get("build.mcu") == "esp32c3":
    env.AppendUnique(LINKFLAGS=["-march=rv32imc_zicsr_zifencei", "-mabi=ilp32"])
    toolchain_dir = env.PioPlatform().get_package_dir("toolchain-riscv32-esp")
    # pioarduino's Windows package may contain an extra riscv32-esp-elf
    # directory (package/riscv32-esp-elf/riscv32-esp-elf/lib). Resolve the
    # actual sysroot instead of silently falling back to the default RV32IMAC
    # libstdc++, whose AMO instructions crash the ESP32-C3.
    sysroot = join(toolchain_dir, "riscv32-esp-elf")
    nested_sysroot = join(sysroot, "riscv32-esp-elf")
    if isdir(join(nested_sysroot, "lib")):
        sysroot = nested_sysroot
    env.PrependUnique(
        LIBPATH=[
            join(
                sysroot,
                "lib",
                "rv32imc_zicsr_zifencei",
                "ilp32",
            )
        ]
    )
    env.AddPostAction("$BUILD_DIR/${PROGNAME}.elf", verify_esp32c3_multilib)
    print("ESP32-C3 multilib linker flags enabled")
