import io.github.jqssun.airplay.receiver.AppearancePolicy

fun main() {
    for (selected in listOf("classic", "light", "dark")) {
        check(AppearancePolicy.resolve(selected) == selected)
    }
    check(AppearancePolicy.resolve("invalid") == "classic")
    check(AppearancePolicy.resolve("") == "classic")
    check(AppearancePolicy.resolve("amber") == "classic")
    println("PASS: 6 appearance checks; three themes, retired Amber falls back to Classic")
}
