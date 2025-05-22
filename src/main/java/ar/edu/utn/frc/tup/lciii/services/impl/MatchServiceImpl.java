package ar.edu.utn.frc.tup.lciii.services.impl;

import ar.edu.utn.frc.tup.lciii.dtos.match.MatchResponseDTO;
import ar.edu.utn.frc.tup.lciii.dtos.match.NewMatchRequestDTO;
import ar.edu.utn.frc.tup.lciii.dtos.round.NewMatchRoundRequestDTO;
import ar.edu.utn.frc.tup.lciii.dtos.round.RoundPlayDTO;
import ar.edu.utn.frc.tup.lciii.dtos.round.RoundResponseDTO;
import ar.edu.utn.frc.tup.lciii.entities.MatchEntity;
import ar.edu.utn.frc.tup.lciii.models.*;
import ar.edu.utn.frc.tup.lciii.repositories.jpa.MatchJpaRepository;
import ar.edu.utn.frc.tup.lciii.services.DeckService;
import ar.edu.utn.frc.tup.lciii.services.MatchService;
import ar.edu.utn.frc.tup.lciii.services.PlayerService;
import ar.edu.utn.frc.tup.lciii.services.RoundService;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class MatchServiceImpl implements MatchService {

    private static final BigDecimal CARDS_LIMIT = BigDecimal.valueOf(7.5);
    private static final BigDecimal APP_THRESHOLD = BigDecimal.valueOf(5);
    private static final BigDecimal CHIPS_PER_ROUND = BigDecimal.valueOf(20);

    @Autowired
    private MatchJpaRepository matchJpaRepository;

    @Autowired
    private PlayerService playerService;

    @Autowired
    private RoundService roundService;

    @Autowired
    private DeckService deckService;

    @Autowired
    private ModelMapper modelMapper;

    @Override
    public List<MatchResponseDTO> getMatchesByPlayer(Long playerId) {
        // DONE: Completar el metodo de manera tal que retorne todas las partidas
        //  en las que haya participado un jugador y que ya hayan finalizado.
        //  Si el jugador no tiene partidas finalizadas debe retornar una exepción del tipo
        //  EntityNotFoundException con el mensaje "The player do not have matches finished"
        List<MatchResponseDTO> matches = new ArrayList<>();
        Optional<List<MatchEntity>> matchEntityList = matchJpaRepository.getAllByPlayerIdAndMatchStatus(playerId, MatchStatus.FINISH);
        if(matchEntityList.isPresent()) {
            // DONE: Recorrer la lista y mapear el objeto
            for (MatchEntity matchEntity : matchEntityList.get()) {
                //matches.add(modelMapper.map(matchEntity, MatchResponseDTO.class));
                MatchResponseDTO matchResponseDTO = modelMapper.map(matchEntity, MatchResponseDTO.class);
                matches.add(matchResponseDTO);
            }
            return matches;
        } else {
            throw new EntityNotFoundException("The player do not have matches finished");
        }
    }

    @Override
    public MatchResponseDTO createMatch(NewMatchRequestDTO newMatchRequestDTO) {
        Player player = playerService.getPlayerById(newMatchRequestDTO.getPlayerId());
        Optional<Match> optionalMatch = this.getPlayingMatch(player.getId());
        if(optionalMatch.isEmpty()) {
            return modelMapper.map(this.createMatch(player), MatchResponseDTO.class);
        } else {
            return modelMapper.map(optionalMatch.get(), MatchResponseDTO.class);
        }
    }

    @Override
    public Match getMatchById(Long id) {
        MatchEntity me = matchJpaRepository.getReferenceById(id);
        if(me != null) {
            Match match = modelMapper.map(me, Match.class);
            return match;
        }else {
            throw new EntityNotFoundException(String.format("The match id %s not found", id));
        }
    }

    @Override
    public MatchResponseDTO getMatchResponseDTOById(Long id) {
        MatchEntity me = matchJpaRepository.getReferenceById(id);
        if(me != null) {
            return modelMapper.map(me, MatchResponseDTO.class);
        }else {
            throw new EntityNotFoundException(String.format("The match id %s not found", id));
        }
    }

    @Transactional
    @Override
    public RoundResponseDTO createRoundMatch(Long matchId, NewMatchRoundRequestDTO newMatchRoundRequestDTO) {
        // DONE: Implementar el metodo de manera tal que haga las siguiestes acciones y validaciones.

        // 1. Validar que el matchId exista en la DB, si no exite retornar una exepción del
        //  tipo EntityNotFoundException con el mensaje "The match id {matchId} not found"
        Match match = this.getMatchById(matchId);

        // 2. Validar que el match le pertenezca al player recibido por parametro, sino retornar
        //  una excepcion del tipo IllegalArgumentException con el mensaje "The match id {matchId} does not belong to player {playerId}"
        if (!match.getPlayer().getId().equals(newMatchRoundRequestDTO.getPlayerId())) {
            throw new IllegalArgumentException(String.format("The match id %d does not belong to player %d", matchId, newMatchRoundRequestDTO.getPlayerId()));
        }

        //  3. Validar que el jugador no tenga rounds sin terminar, en ese caso, retornar el round sin terminar.
        //  Si hay mas de uno retornar el primer elemento de la lista.
        List<Round> roundsUnfinished = roundService.getUnfinishedRounds(matchId);
        if(!roundsUnfinished.isEmpty()) {
            return modelMapper.map(roundsUnfinished.get(0), RoundResponseDTO.class);
        }

        //  4. Validar que el jugador tenga suficientes fichas para iniciar un nuevo round, si no retornar una excecion del tipo
        //  ResponseStatusException 403 con el mensaje "Insufficient balance"
        if (!(match.getPlayer().getBalanceChips().compareTo(CHIPS_PER_ROUND) >= 0)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient balance");
        }

        // 5 ------INICIO--------
        Round round = new Round();
        round.setMatchId(matchId);
        //  5.a. Crear un mazo (Deck y mesclarlo)
        Deck deck = deckService.createDeck();
        deckService.shuffleDeck(deck);
        round.setDeck(deck);

        //  5.b. Asignar la primera carta al jugador y la segunda a la app
        List<Card> playerCards = new ArrayList<>();
        playerCards.add(deck.getCards().get(0));
        round.setPlayerCards(playerCards);

        List<Card> appCards = new ArrayList<>();
        appCards.add(deck.getCards().get(1));
        round.setAppCards(appCards);

        //  5.c. Actualizar el indice del mazo (Round.deckIndexPosition)
        round.setDeckIndexPosition(2);

        //  5.d. Calcular los valores de las cartas de cada jugador (Round.playerCardsValue y Round.appCardsValue)
        round.setPlayerCardsValue(getCardsValue(round.getPlayerCards()));
        round.setAppCardsValue(getCardsValue(round.getAppCards()));

        //  5.e. Calcular el estado de la mano de cada jugador (Round.roundHandStatusPlayer y Round.roundHandStatusApp)
        round.setRoundHandStatusPlayer(calculateRoundHand(round.getPlayerCardsValue()));
        round.setRoundHandStatusApp(calculateAppHand(round.getAppCardsValue()));

        //  5.f. Asignar las fichas en juego (Round.chipsInPlay)
        round.setChipsInPlay(CHIPS_PER_ROUND.multiply(BigDecimal.valueOf(2)));

        // 5 -------FIN-------
        round = roundService.saveRound(round);
        return modelMapper.map(round, RoundResponseDTO.class);
    }

    @Override
    public RoundResponseDTO playRoundMatch(Long matchId, Long roundId, RoundPlayDTO roundPlayDTO) {
        // DONE: Implementar el metodo de manera tal que haga las siguiestes acciones y validaciones:

        //  1. Validar que el matchId exista en la DB, si no exite retornar una exepción del
        //  tipo EntityNotFoundException con el mensaje "The match id {matchId} not found"
        Match match = this.getMatchById(matchId);

        //  2. Validar que el roundId exista en la DB, si no exite retornar una exepción del
        //  tipo EntityNotFoundException con el mensaje "The round id {roundId} not found"
        Round round = roundService.getRoundById(roundId);

        //  3. Validar que el match le pertenezca al player recibido por parametro, sino retornar
        //  una excepcion del tipo IllegalArgumentException con el mensaje "The match id {matchId} does not belong to player {playerId}"
        if (!match.getPlayer().getId().equals(roundPlayDTO.getPlayerId())) {
            throw new IllegalArgumentException(String.format("The match id %d does not belong to player %d", matchId, roundPlayDTO.getPlayerId()));
        }

        //  4. Validar que el round le pertenezca al match, sino retornar
        //  una excepcion del tipo IllegalArgumentException con el mensaje "The round id {roundId} does not belong to match {matchId}"
        if(!match.getRounds().contains(round)) {
            throw new IllegalArgumentException(
                    String.format("The round id %s does not belong to match %s", roundId, matchId));
        }

        //  5. Validar que el round no este terminado (winner not null) y el jugador aun este en juego
        //  (Round.roundHandStatusPlayer.IN_GAME), sino retornar ResponseStatusException 403 con el mensaje "Round is end"
        if (!(round.getWinner() == null && round.getRoundHandStatusPlayer().equals(RoundHandStatus.IN_GAME))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Round is end");
        }

        //  6. Si las condiciones previas pasan correctamente, se debe ejecutar la acción del jugador.
        //  Para eso hacer las siguientes acciones:
        //  6.a. Si la accion es STOP entonces:
        //    - Cambiar el estado de la mano del jugador (Round.roundHandStatusPlayer.STOPPED)
        //    - Ejecutar la jugada de la app hasta que obtenga al menos 5 puntos en sus cartas o se pase.
        //    - Calcular los valores de las cartas de la app (Round.appCardsValue)
        //    - Calcular el estado de la mano de la app (Round.roundHandStatusApp)
        //    - Calcular el ganador y pagar la ronda si aplica.
        //  6.b. Si la accion es NEW_CARD entonces:
        //    - Asignar al jugador la siguiente carta en el mazo.
        //    - Calcular los valores de las cartas del jugador (Round.playerCardsValue)
        //    - Calcular el estado de la mano del jugador (Round.roundHandStatusApp).
        //      * Si el resultado es EXCEEDED, entonces ejecutar los pasos de la app como si el jugador hubiera parado.
        //      * Si el resultado es IN_GAME, entonces guardar los datos y retornar la respuesta.
        if(roundPlayDTO.getDecision().equals(RoundDecision.STOP)) {
            round.setRoundHandStatusPlayer(RoundHandStatus.STOPPED);
            playAppRound(round, match.getPlayer().getId());
        } else {
            playPlayerRound(round);
            if(round.getRoundHandStatusPlayer().equals(RoundHandStatus.EXCEEDED)) {
                playAppRound(round, match.getPlayer().getId());
            }
        }
        round = roundService.saveRound(round);
        return modelMapper.map(round, RoundResponseDTO.class);
    }

    @Override
    public Optional<Match> getPlayingMatch(Long playerId) {
        //DONE: Implementar el metodo para que retorne, si existe, el match que esté en estado PLAYING.
        // Si existieran mas de uno, (Situación que no debiera ser posible) retornar el primero
        Optional<List<MatchEntity>> matchEntityList = matchJpaRepository.getAllByPlayerIdAndMatchStatus(playerId, MatchStatus.PLAYING);
        if (matchEntityList.isPresent()) {
            matchEntityList.get().get(0);
        }
        return Optional.empty();
    }

    @Override
    public MatchResponseDTO finishMatch(Long id) {
        Match match = this.getMatchById(id);
        Optional<Round> unfinishedRound = hasUnfinishedRound(match);
        if(unfinishedRound.isPresent()) {
            roundService.forceEndOfRound(unfinishedRound.get().getId());
            BigDecimal impactBalance = unfinishedRound.get().getChipsInPlay().negate();
            playerService.updatePlayerBalance(match.getPlayer().getId(), impactBalance);
        }
        match.setMatchStatus(MatchStatus.FINISH);
        MatchEntity matchEntity = matchJpaRepository.save(modelMapper.map(match, MatchEntity.class));
        return modelMapper.map(matchEntity, MatchResponseDTO.class);
    }

    private void playAppRound(Round round, Long playerId) {
        // DONE: Completar el metodo para que ejecuite los pasos descriptos
        while(round.getRoundHandStatusApp().equals(RoundHandStatus.IN_GAME)) {
            // 1 - Tomar del round las cartas de la app y agregarle la siguiente carta del mazo -> Ayuda: deckService.takeCard
            List<Card> appCards = round.getAppCards();
            appCards.add(deckService.takeCard(round.getDeck(), round.getDeckIndexPosition()));
            // 2 - Actualizar el indice del mazo (deckIndexPosition)
            round.setDeckIndexPosition(round.getDeckIndexPosition() + 1);
            // 3 - Calcular la sumarización de las cartas de la app -> Ayuda: getCardsValue
            round.setAppCardsValue(getCardsValue(round.getAppCards()));
            // 4 - Calcular el estado del round para la app -> Ayuda: calculateAppHand
            round.setRoundHandStatusApp(calculateAppHand(round.getAppCardsValue()));
        }
        calculateWinner(round, playerId);
    }

    private void calculateWinner(Round round, Long playerId) {
        if(round.getRoundHandStatusPlayer().equals(RoundHandStatus.EXCEEDED)) {
            if(round.getRoundHandStatusApp().equals(RoundHandStatus.EXCEEDED)) {
                round.setWinner(RoundWinner.APP);
            } else {
                round.setWinner(RoundWinner.APP);
                // DONE: Descontar la apuesta del player del balance usando playerService.updatePlayerBalance
                playerService.updatePlayerBalance(playerId, CHIPS_PER_ROUND.negate());
            }
        } else {
            if(round.getRoundHandStatusApp().equals(RoundHandStatus.EXCEEDED)) {
                round.setWinner(RoundWinner.PLAYER);
                if(round.getPlayerCardsValue().compareTo(CARDS_LIMIT) == 0) {
                    // DONE: Sumar la apuesta del player * 2 al balance usando playerService.updatePlayerBalance
                    playerService.updatePlayerBalance(playerId, CHIPS_PER_ROUND.multiply(BigDecimal.valueOf(2)));
                } else {
                    // DONE: Sumar la apuesta del player al balance usando playerService.updatePlayerBalance
                    playerService.updatePlayerBalance(playerId, CHIPS_PER_ROUND);
                }
            } else {
                if(round.getPlayerCardsValue().compareTo(round.getAppCardsValue()) > 0) {
                    round.setWinner(RoundWinner.PLAYER);
                    if(round.getPlayerCardsValue().compareTo(CARDS_LIMIT) == 0) {
                        // DONE: Sumar la apuesta del player * 2 al balance usando playerService.updatePlayerBalance
                        playerService.updatePlayerBalance(playerId, CHIPS_PER_ROUND.multiply(BigDecimal.valueOf(2)));
                    } else {
                        // DONE: Sumar la apuesta del player al balance usando playerService.updatePlayerBalance
                        playerService.updatePlayerBalance(playerId, CHIPS_PER_ROUND);
                    }
                } else {
                    round.setWinner(RoundWinner.APP);
                    // DONE: Descontar la apuesta del player del balance usando playerService.updatePlayerBalance
                    playerService.updatePlayerBalance(playerId, CHIPS_PER_ROUND.negate());
                }
            }
        }
    }

    private void playPlayerRound(Round round) {
        round.getPlayerCards().add(deckService.takeCard(round.getDeck(), round.getDeckIndexPosition()));
        round.setDeckIndexPosition(round.getDeckIndexPosition() + 1);
        round.setPlayerCardsValue(getCardsValue(round.getPlayerCards()));
        round.setRoundHandStatusPlayer(calculateRoundHand(round.getPlayerCardsValue()));
    }

    private Match createMatch(Player player) {
        // DONE: Crear un match con los siguientes datos:
        //  - El player recibido por parametro
        //  - El Match debe tener el estado PLAYING
        //  - Guardar el Match y retornar la respuesta mapeada a Match
        Match match = new Match();
        List<Round> rounds = new ArrayList<>();
        match.setRounds(rounds);
        match.setPlayer(player);
        match.setMatchStatus(MatchStatus.PLAYING);
        MatchEntity matchEntity = modelMapper.map(match, MatchEntity.class);
        MatchEntity matchEntitySaved = matchJpaRepository.save(matchEntity);
        return modelMapper.map(matchEntitySaved, Match.class);
    }

    private Optional<Round> hasUnfinishedRound(Match match) {
        // DONE: Implementar el metódo de manera tal que si hay un round sin terminar (winner == null),
        //  lo retorne en un Optional, sino, el Optional se retorna vacio.
        for (Round round : match.getRounds()) {
            if (round.getWinner() == null) {
                return Optional.of(round);
            }
        }
        return Optional.empty();
    }

    private BigDecimal getCardsValue(List<Card> cardsToSummarize) {
        // DONE: Sumar los valores de todas las cartas recibidas por parametro y retornar el valor de la sumarización
        BigDecimal sum = new BigDecimal(0);
        for (Card card : cardsToSummarize) {
            sum = card.getValue();
        }
        return sum;
    }

    private RoundHandStatus calculateRoundHand(BigDecimal cardsValue) {
        // DONE: Retornar RoundHandStatus.EXCEEDED si excede de 7.5, de lo contrario retornar RoundHandStatus.IN_GAME
        if (cardsValue.intValue() > 7.5) {
            return RoundHandStatus.EXCEEDED;
        } else {
            return RoundHandStatus.IN_GAME;
        }
    }

    private RoundHandStatus calculateAppHand(BigDecimal cardsValue) {
        // DONE: Retornar RoundHandStatus.EXCEEDED si excede de 7.5 o RoundHandStatus.STOPPED si excede 5
        //  de lo contrario retornar RoundHandStatus.IN_GAME
        if (cardsValue.intValue() > 7.5) {
            return RoundHandStatus.EXCEEDED;
        } else if (cardsValue.intValue() > 5) {
            return RoundHandStatus.STOPPED;
        } else{
            return RoundHandStatus.IN_GAME;
        }
    }
}
