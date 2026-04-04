package pl.neopak.rma.returnmanagement.service;

import org.springframework.stereotype.Service;
import pl.neopak.rma.returnmanagement.domain.model.ReturnStatus;
import pl.neopak.rma.returnmanagement.domain.model.RmaNumber;
import pl.neopak.rma.returnmanagement.port.in.QueryReturnRequestUseCase;
import pl.neopak.rma.returnmanagement.port.out.ReturnRequestRepository;

import java.util.List;
import java.util.Optional;

@Service
public class QueryReturnRequestService implements QueryReturnRequestUseCase {

    private final ReturnRequestRepository repository;

    public QueryReturnRequestService(ReturnRequestRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Object> findByRmaNumber(String rmaNumber) {
        return repository.findByRmaNumber(RmaNumber.of(rmaNumber))
                .map(rma -> (Object) rma);
    }

    @Override
    public List<Object> findByStatus(String status) {
        ReturnStatus returnStatus = ReturnStatus.valueOf(status.toUpperCase());
        return repository.findByStatuses(List.of(returnStatus))
                .stream()
                .map(rma -> (Object) rma)
                .toList();
    }
}
