package net.openan.a2at.sample.service_recovery;

/**
 * One verification outcome of the service-recovery sample.
 *
 * @param name check name identifying the verified expectation
 * @param passed whether the expectation held
 * @param detail human-readable outcome detail, never null
 * @since 2026-08
 */
public record VerificationCheck(String name, boolean passed, String detail) {

    /**
     * Creates a passed check.
     *
     * @param name check name
     * @param detail outcome detail
     * @return passed check
     */
    public static VerificationCheck passed(String name, String detail) {
        return new VerificationCheck(name, true, detail);
    }

    /**
     * Creates a failed check.
     *
     * @param name check name
     * @param detail outcome detail
     * @return failed check
     */
    public static VerificationCheck failed(String name, String detail) {
        return new VerificationCheck(name, false, detail);
    }
}
