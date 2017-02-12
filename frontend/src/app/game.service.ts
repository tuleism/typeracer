import { WebsocketService } from './websocket.service';
import { Injectable } from '@angular/core';
import { Subject } from 'rxjs'
import * as Rx from 'rxjs/Rx'

import { GameOutput, RaceInit, RaceState } from './model'

export interface Message {
	message: string
}

@Injectable()
export class GameService {
  init: Subject<RaceInit> = new Subject<RaceInit>();
  raceState: Subject<RaceState> = new Subject<RaceState>();
  send: Subject<Object> = new Subject<Object>();
  playerName: string;

  constructor(private _websocketService: WebsocketService) { }

  setPlayerName(name: string): void {
    this.playerName = name;

    let ws = this._websocketService
      .connect("ws://localhost:5000?name=" + this.playerName)

    ws.filter((response) => response.data != 'keepalive')
      .forEach((response) => {
        let data = JSON.parse(response.data)
        if (data.quote) {
          this.init.next(data)
        }
        if (data.current) {
          this.raceState.next(data)
        }
      })

    let out = <Subject<Object>>ws.map(
      (response) => response.data
    )

    this.send.subscribe(
      (msg) => out.next(msg)
    )
  }

  sendOut(msg: Object): void {
    this.send.next(msg)
  }
}
