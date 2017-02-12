import { Component, OnInit, ViewChild } from '@angular/core';
import { Subject, BehaviorSubject } from 'rxjs'
import * as Rx from 'rxjs/Rx'
import { ModalDirective } from 'ng2-bootstrap/modal';

import { GameService } from 'app/game.service'

enum InRace {
  Waiting,
  Racing,
  Finished,
}

@Component({
  selector: 'app-typing',
  templateUrl: './typing.component.html'
})
export class TypingComponent implements OnInit {
  public currentInput: string;
  public warn: boolean;

  public pastWords: string[] = [];
  public currentWord: string = "";
  public futureWords: string[] = [];

  public inRace: InRace = InRace.Waiting;
  @ViewChild('childModal') public childModal: ModalDirective;
  public modalContent: string;

  constructor(private _gameService: GameService) { }

  ngOnInit() {
    this._gameService.init.subscribe(
      raceInit => {
        var parts = raceInit.quote.split(" ");
        this.pastWords = [];
        this.currentWord = parts[0];
        this.futureWords = parts.splice(1);

        if (raceInit.timeLeft) {
          var raceTime = Date.now() + 1000 * raceInit.timeLeft;
          var timer = Rx.Observable.timer(0, 1000).timestamp().take(raceInit.timeLeft + 1);
          timer.subscribe(
            x => {
              var timeLeft = raceInit.timeLeft - x.value;
              this.modalContent = "Start in " + timeLeft;
              if (timeLeft == 1) {
                this.childModal.hide();
              }
            },
            _ => _,
            () => this.inRace = InRace.Racing
          )
          this.childModal.show();
        }
      }
    )
  }

  onInputChange(): void {
    if (this.currentWord == this.currentInput && this.futureWords.length == 0) {
      this.pastWords.push(this.currentWord);
      this.currentInput = "";
      this.inRace = InRace.Finished;
      this.currentWord = ""
      this._gameService.sendOut(this.pastWords.length)
    } else if (this.currentWord + " " == this.currentInput) {
      this.pastWords.push(this.currentWord);
      this.currentWord = this.futureWords[0];
      this.futureWords = this.futureWords.splice(1);
      this.currentInput = "";
      this._gameService.sendOut(this.pastWords.length)
    } else if (this.currentWord.startsWith(this.currentInput)) {
      this.warn = false;
    } else {
      this.warn = true;
    }
  }

  isRacing(): boolean {
    return this.inRace == InRace.Racing;
  }

  hasFinished(): boolean {
    return this.inRace == InRace.Finished;
  }

  disableRaceInput(): boolean {
    return this.inRace == InRace.Waiting;
  }

  replay(): void {
    this._gameService.sendOut("newGame");
  }

  getInputStyle(): string {
    if (this.warn) {
      return "#d08383"
    } else {
      return ""
    }
  }

  getCurrentWordStyle(): string {
    if (this.warn) {
      return "#d08383"
    } else {
      return "#99cc00"
    }
  }
}
