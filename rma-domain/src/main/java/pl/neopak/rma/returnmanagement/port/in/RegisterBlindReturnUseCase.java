package pl.neopak.rma.returnmanagement.port.in;

public interface RegisterBlindReturnUseCase {
    String registerBlind(String parcelDescription, String warehouseWorkerId);
}
