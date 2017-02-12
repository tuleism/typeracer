import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpModule } from '@angular/http';

import { ProgressbarModule } from 'ng2-bootstrap/progressbar';
import { ModalModule } from 'ng2-bootstrap/modal';

import { AppComponent } from './app.component';
import { TypingComponent } from './typing/typing.component';

import { GameService } from './game.service';
import { WebsocketService } from './websocket.service';
import { RacingComponent } from './racing/racing.component';

@NgModule({
  declarations: [
    AppComponent,
    TypingComponent,
    RacingComponent,
  ],
  imports: [
    BrowserModule,
    FormsModule,
    HttpModule,
    ProgressbarModule.forRoot(),
    ModalModule.forRoot(),
  ],
  providers: [WebsocketService, GameService],
  bootstrap: [AppComponent]
})
export class AppModule { }
