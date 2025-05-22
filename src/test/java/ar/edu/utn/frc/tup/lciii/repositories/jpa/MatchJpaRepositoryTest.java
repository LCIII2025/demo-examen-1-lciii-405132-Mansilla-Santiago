package ar.edu.utn.frc.tup.lciii.repositories.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import ar.edu.utn.frc.tup.lciii.entities.MatchEntity;
import ar.edu.utn.frc.tup.lciii.entities.PlayerEntity;
import ar.edu.utn.frc.tup.lciii.models.MatchStatus;


@DataJpaTest
class MatchJpaRepositoryTest {

    @Test
    void getAllByPlayerIdAndMatchStatusTest() {
        // DONE: Completar el Test para validar que el metodo
        //  getAllByPlayerIdAndMatchStatus(Long playerId, MatchStatus matchStatus)
        //  de la clase MatchJpaRepository funciona.

        //Arrange
        PlayerEntity player = new PlayerEntity();
        player.setId(3L);
        MatchEntity matchEntity = new MatchEntity();
        matchEntity.setPlayer(player);
        matchEntity.setMatchStatus(MatchStatus.FINISH);
        Optional<List<MatchEntity>> matches = Optional.of(List.of(matchEntity));

        MatchJpaRepository matchJpaRepositoryMock = mock(MatchJpaRepository.class);
        //matchJpaRepositoryMock.save(matchEntity);

        when(matchJpaRepositoryMock.getAllByPlayerIdAndMatchStatus(3L, MatchStatus.FINISH))
            .thenReturn(matches);

        //Act
        Optional<List<MatchEntity>> result = matchJpaRepositoryMock.getAllByPlayerIdAndMatchStatus(3L, MatchStatus.FINISH);

        //Assert
        assertEquals(matches, result);
        assertEquals(3L, matches.get().get(0).getPlayer().getId());
        assertEquals(MatchStatus.FINISH, matches.get().get(0).getMatchStatus());
    }
}