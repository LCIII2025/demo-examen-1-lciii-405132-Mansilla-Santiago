package ar.edu.utn.frc.tup.lciii.repositories.jpa;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;



@DataJpaTest
class MatchJpaRepositoryTest {

    // @
    // private MatchJpaRepository matchJpaRepository;

    // @Test
    // void getAllByPlayerIdAndMatchStatusTest() {
    //     // DONE: Completar el Test para validar que el metodo
    //     //  getAllByPlayerIdAndMatchStatus(Long playerId, MatchStatus matchStatus)
    //     //  de la clase MatchJpaRepository funciona.

    //     //Arrange
    //     PlayerEntity player = new PlayerEntity();
    //     player.setId(3L);

    //     MatchEntity matchEntity = new MatchEntity();
    //     matchEntity.setPlayer(player);
    //     matchEntity.setMatchStatus(MatchStatus.FINISH);

    //     Optional<List<MatchEntity>> matches = Optional.of(List.of(matchEntity));
    //     when(matchJpaRepository.getAllByPlayerIdAndMatchStatus(3L, MatchStatus.FINISH)).


       
    //     //Act
    //     Optional<List<MatchEntity>> result = matchJpaRepository.getAllByPlayerIdAndMatchStatus(3L, MatchStatus.FINISH);

    //     //Assert
    //     assertEquals(matches, result);
    //     assertEquals(3L, matches.get().get(0).getPlayer().getId());
    //     assertEquals(MatchStatus.FINISH, matches.get().get(0).getMatchStatus());
    // }
}