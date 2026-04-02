package pl.neopak.rma.returnmanagement.domain.model;

public final class PackageDimensions {

    private final int weightKg;
    private final int lengthCm;
    private final int widthCm;
    private final int heightCm;

    private PackageDimensions(int weightKg, int lengthCm, int widthCm, int heightCm) {
        if (weightKg <= 0 || lengthCm <= 0 || widthCm <= 0 || heightCm <= 0) {
            throw new IllegalArgumentException("All dimensions and weight must be greater than 0");
        }
        this.weightKg = weightKg;
        this.lengthCm = lengthCm;
        this.widthCm = widthCm;
        this.heightCm = heightCm;
    }

    public static PackageDimensions of(int weightKg, int lengthCm, int widthCm, int heightCm) {
        return new PackageDimensions(weightKg, lengthCm, widthCm, heightCm);
    }

    public boolean isSuitableForCourier(CourierCode courier) {
        switch (courier) {
            case INPOST:
                int maxSideInpost = Math.max(lengthCm, Math.max(widthCm, heightCm));
                return weightKg <= 25 && maxSideInpost <= 64;
            case DPD:
                int sumDpd = lengthCm + widthCm + heightCm;
                return weightKg <= 31 && sumDpd <= 250;
            case GLS:
                int maxSideGls = Math.max(lengthCm, Math.max(widthCm, heightCm));
                return weightKg <= 40 && maxSideGls <= 200;
            case ORLEN:
                return weightKg <= 30;
            case GEIS:
                return true;
            default:
                throw new IllegalArgumentException("Unknown courier: " + courier);
        }
    }

    public int getWeightKg() {
        return weightKg;
    }

    public int getLengthCm() {
        return lengthCm;
    }

    public int getWidthCm() {
        return widthCm;
    }

    public int getHeightCm() {
        return heightCm;
    }
}
